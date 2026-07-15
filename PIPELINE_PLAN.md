# Unified load → generate → accept pipeline — design

## Goal
Let a curator trigger the whole workflow for one hierarchy in a single request — **load**
(retrieve hierarchy from Kramerius) → **generate** (run an OCR engine over the pages) →
**accept** (upload chosen versions back to Kramerius + rebuild/reindex) — with correct
ordering, per-stage concurrency, progress, and fail-fast error handling.

Today the three stages exist only as independent, manually-triggered batch types with no
ordering or chaining. This adds an orchestration layer **on top of** the existing stage
processes without rewriting them.

## Decisions (locked)
- **Model B — chained child batches.** A parent `PIPELINE` batch spawns one real typed
  child batch per selected stage; the scheduler runs them in order; each child keeps running
  in its own typed pool at its own concurrency cap.
- **Configurable stages, Accept opt-in.** The request lists which stages to run. Accept
  (destructive — writes to Kramerius) is only included when explicitly requested.
- **Fail-fast.** Any stage `FAILED` fails the pipeline and skips remaining stages. An
  *empty-but-successful* stage (e.g. generate found 0 pages) still proceeds.

## Why the inputs can be wired up-front (key simplification)
The pipeline params fully determine every stage's input, so all child batches can be created
at submit time — no runtime data hand-off between stages is needed, only ordering:

| Stage | Child batch type | Input (all known at submit) |
|---|---|---|
| Load | `RETRIEVE_HIERARCHY` | `pid`, `instance` |
| Generate | `GENERATE_FOR_HIERARCHY` | `pid`, `instance`, `engine`, `data={scope}` |
| Accept | `ACCEPT_VERSIONS` | `data=AltoVersionSearchFilter{ hierarchyPid: pid, states:[PENDING], users:[engineUserId], instance }` |

The accept filter is derivable because generate writes **PENDING** versions owned by the
engine user for pages under `pid`. (Caveat: pre-existing PENDING versions from the same
engine under that hierarchy would also match — acceptable; can be tightened later with
`createdAfter` stamped at pipeline start if we want strict scoping.)

Ordering (don't accept before generate produced the PENDING versions) is enforced by the
scheduler, not by data passing.

---

## Data model changes

### `Batch` entity — two nullable fields
- `parentBatchId : Integer` — FK to `batches.id`; set on child stage batches, null for the
  parent and for all standalone (non-pipeline) batches. Indexed.
- `stageOrder : Integer` — 0-based position within the pipeline (compacted over the
  *selected* stages, so generate+accept-only ⇒ generate=0, accept=1). Null for standalone.

### `BatchType` — add `PIPELINE`
The parent row. It is **never dispatched** to a worker — it has no process and is skipped by
`ProcessDispatcher`. Its state is derived by the coordinator (below). Reusing `Batch` (rather
than a new entity) keeps `/api/batches` listing/search uniform; children link via
`parentBatchId`.

### Migration
Add `parent_batch_id` (int, null, FK `batches(id)`) + `stage_order` (int, null) and an index
on `parent_batch_id`, via the project's existing schema mechanism.

---

## Scheduling & coordination

### 1. Dependency-aware claim (the only scheduler change)
`BatchService.claimOldestPlannedBatchByType(type)` must not claim a child whose predecessor
sibling isn't finished. Add to the claim predicate:

```
parent_batch_id IS NULL
OR NOT EXISTS (
    SELECT 1 FROM batches s
    WHERE s.parent_batch_id = b.parent_batch_id
      AND s.stage_order    < b.stage_order
      AND s.state         <> 'DONE'
)
```

Standalone batches are unaffected. Because stages are strictly sequential, at most one child
per pipeline is ever eligible at a time; cross-pipeline parallelism is unchanged (book A's
generate can run while book B retrieves), and each stage still obeys its own
`max-processes-per-type` cap. **No OCR-cap bypass** (the main risk of Model A).

### 2. Pipeline coordinator (new) — runs each scheduler tick (5s)
For each parent `PIPELINE` not in a terminal state, load its children and:
- **any child `FAILED`** → set parent `FAILED`; set every still-`PLANNED` child → `FAILED`
  with log "skipped: earlier stage failed". (Needed — a non-DONE predecessor means downstream
  children can never be claimed, so they'd otherwise hang forever.)
- **all children `DONE`** → parent `DONE`.
- **otherwise** → parent `RUNNING` (for display; optional).

This is the single place that owns failure propagation and parent status — the stage
processes stay unaware of siblings. Crash recovery already resets `RUNNING → FAILED` on
startup; the coordinator then propagates as above, so pipelines recover consistently.

### 3. `ProcessDispatcher`
Skip `PIPELINE` in the per-type dispatch loop (no executor/factory for it).

---

## API

`POST /api/pipelines` (new `PipelineController` + `PipelineFacade`) — body:

```jsonc
{
  "pid": "uuid:...",              // hierarchy to process
  "instance": "k7-mzk",
  "engine": "pero-vut",          // required if GENERATE included
  "scope": "NO_PENDING_NOR_ACTIVE", // generate scope; required if GENERATE included
  "stages": ["RETRIEVE", "GENERATE", "ACCEPT"], // subset, canonical order
  "priority": "MEDIUM"
}
```

`PipelineFacade.create(...)` (transactional):
1. Validate: `stages` non-empty; `instance` required if RETRIEVE; `engine`+`scope` required if
   GENERATE; ACCEPT allowed only when explicitly listed. Resolve `engineUserId` from `engine`.
2. Create parent `PIPELINE` batch (`pid`, `createdBy`, `priority`, state `PLANNED`).
3. Create one child batch per selected stage in canonical order with `parentBatchId`,
   compacted `stageOrder`, and the inputs from the table above; all `PLANNED`.
4. Return parent id; children appear under it via `parentBatchId`.

Response/listing: `GET /api/batches?parentBatchId=…` (or include children in a pipeline DTO)
so the UI can show the parent + its stages and per-stage progress.

Existing single-stage endpoints stay as-is.

---

## Behavior summary
- Stages run strictly in order; only one child per pipeline active at a time; different
  pipelines interleave through the existing typed pools.
- Fail-fast: first `FAILED` stage → pipeline `FAILED`, downstream skipped.
- Empty success proceeds: generate finding 0 pages → accept runs, matches nothing → pipeline
  `DONE`.
- Engine-agnostic: `engine` is a parameter, so the future native **tuzka** engine plugs in as
  just another value with **no pipeline change**.

---

## Files to add / change
- `domain/enums/BatchType.java` — add `PIPELINE`.
- `domain/model/Batch.java` — add `parentBatchId`, `stageOrder` (+ migration).
- `domain/repository/BatchRepository.java` — dependency-aware claim query; `findByParentBatchId`.
- `domain/service/BatchService.java` — updated claim; `createPipeline(...)`; child creation helpers.
- `infrastructure/process/PipelineCoordinator.java` (new) — reconcile parents each tick.
- `infrastructure/process/ProcessScheduler.java` — call `pipelineCoordinator.reconcile()` per tick.
- `infrastructure/process/ProcessDispatcher.java` — skip `PIPELINE` type.
- `domain/service/dto/PipelineInput.java` (new) — request DTO (pid, instance, engine, scope, stages, priority).
- `presentation/rest/PipelineController.java` + `presentation/facade/PipelineFacade.java` (new).
- Tests:
  - claim ordering (child#2 not claimed until child#1 DONE; standalone unaffected);
  - coordinator (fail propagation → downstream FAILED + parent FAILED; all DONE → parent DONE);
  - `PipelineFacade.create` (parent + N children, correct stageOrder + inputs, validation);
  - end-to-end with mocked stage services (RETRIEVE→GENERATE→ACCEPT happy path; mid-stage failure).

## Sequencing
1. Schema + `Batch` fields + `BatchType.PIPELINE`.
2. Dependency-aware claim + tests.
3. `PipelineCoordinator` + scheduler wiring + `ProcessDispatcher` skip + tests.
4. `PipelineInput` + `BatchService.createPipeline` + facade/controller + tests.
5. Listing/DTO for parent+children.
6. Manual end-to-end against dev (RETRIEVE→GENERATE→ACCEPT on a small book).

## Deferred (not in this pipeline work)
- Native **tuzka** engine (blocked on API spec) — slots in as a generate `engine` later.
- Phase 2 (drop Hibernate Search → Postgres search; delete `REINDEX`).
- "Retry from failed stage" (re-plan the failed + downstream children) — nice-to-have.
- Strict accept scoping via `createdAfter` stamped at pipeline start, if same-engine
  pre-existing PENDING versions prove a problem.
