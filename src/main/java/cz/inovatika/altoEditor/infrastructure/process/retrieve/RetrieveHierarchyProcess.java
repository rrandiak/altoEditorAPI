package cz.inovatika.altoEditor.infrastructure.process.retrieve;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import cz.inovatika.altoEditor.config.properties.BatchProperties;
import cz.inovatika.altoEditor.domain.enums.BatchState;
import cz.inovatika.altoEditor.domain.enums.Model;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.model.DigitalObject;
import cz.inovatika.altoEditor.domain.service.AltoVersionService;
import cz.inovatika.altoEditor.domain.service.BatchService;
import cz.inovatika.altoEditor.domain.service.ObjectHierarchyService;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusObjectMetadata;
import cz.inovatika.altoEditor.infrastructure.process.templates.BatchProcess;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RetrieveHierarchyProcess extends BatchProcess {

    private static final int QUEUE_CAPACITY = 10_000;
    private static final int PROGRESS_EVERY_N_ITEMS = 100;
    private static final long POLL_TIMEOUT_MS = 500;

    private final BatchService batchService;
    private final KrameriusService krameriusService;
    private final AltoVersionService altoVersionService;
    private final ObjectHierarchyService objectHierarchyService;
    private final BatchProperties batchProperties;

    private final Long krameriusUserId;

    public RetrieveHierarchyProcess(
            BatchService batchService,
            KrameriusService krameriusService,
            AltoVersionService altoVersionService,
            ObjectHierarchyService objectHierarchyService,
            BatchProperties batchProperties,
            Long krameriusUserId,
            Batch batch) {

        super(batch.getId(), batch.getPriority(), batch.getCreatedAt(), batch.getType());
        this.batchService = batchService;
        this.krameriusService = krameriusService;
        this.altoVersionService = altoVersionService;
        this.objectHierarchyService = objectHierarchyService;
        this.batchProperties = batchProperties;
        this.krameriusUserId = krameriusUserId;
    }

    /**
     * Runs the hierarchy retrieval process
     * 
     * The expansion is done in a breadth-first manner, starting from the root
     * object.
     * Expansion should be a lot faster than retrieval, so there is no need to have
     * more
     * than one producer thread. Also the producer thread is switching to consumer
     * mode
     * on full queue to avoid blocking the producer thread. This is because the
     * loading of metadata
     * on larger number of objects would be much faster than the retrieval of the
     * ALTO bytes and
     * we want to have natural way of limiting the queue size.
     */
    @Override
    public void run() {
        Batch batch = batchService.getById(batchId);
        String instance = batch.getInstance();
        String originPid = batch.getPid();

        int workers = Math.max(1, batchProperties.getWorkerThreads() - 1);
        ExecutorService pool = Executors.newFixedThreadPool(workers);

        BlockingQueue<KrameriusObjectMetadata> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

        AtomicBoolean producerDone = new AtomicBoolean(false);
        AtomicInteger processed = new AtomicInteger(0);
        AtomicReference<Throwable> firstError = new AtomicReference<>();

        try {
            batchService.setState(batch, BatchState.RUNNING);
            batchService.setProcessedItemCount(batch, 0);

            KrameriusObjectMetadata root = krameriusService.getObjectMetadata(originPid, instance);
            if (root == null) {
                batchService.setFailed(batch, "Object " + originPid + " not found in instance " + instance);
                return;
            }

            processOriginItem(root, instance);
            processed.incrementAndGet();

            // Producer: fills queue; put() blocks when full so consumers can drain
            pool.submit(() -> {
                try {
                    expandHierarchy(root, instance, queue, batch);
                } catch (Exception e) {
                    firstError.compareAndSet(null, e);
                } finally {
                    producerDone.set(true);
                }
            });

            // Consumer loop: exit on any error (producer or any consumer) or when producer done and queue empty
            Runnable consumer = () -> {
                try {
                    while (true) {
                        if (firstError.get() != null) {
                            break;
                        }
                        KrameriusObjectMetadata m = queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                        if (m != null) {
                            try {
                                processItem(m, instance, batch, processed);
                            } catch (Exception e) {
                                firstError.compareAndSet(null, e);
                                break;
                            }
                        } else if (producerDone.get() && queue.isEmpty()) {
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };

            // Worker consumers
            for (int i = 0; i < workers - 1; i++) {
                pool.submit(consumer);
            }

            // Main thread as consumer
            consumer.run();

            // Propagate first failure (producer or any consumer) so batch is marked failed
            Throwable t = firstError.get();
            if (t != null) {
                throw t instanceof RuntimeException ? (RuntimeException) t : new RuntimeException(t);
            }

            // Wait for the producer and consumers to finish
            // Should not take more than 1 minute, because the work should be done by now
            pool.shutdown();
            try {
                if (!pool.awaitTermination(1, java.util.concurrent.TimeUnit.MINUTES)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                pool.shutdownNow();
            }

            batchService.setProcessedItemCount(batch, processed.get());
            batchService.setState(batch, BatchState.DONE);

        } catch (Exception ex) {
            log.error("RetrieveHierarchyProcess batch {} failed: {}", batchId, ex.getMessage(), ex);

            try {
                batchService.setFailed(batch, ex.getMessage());
            } catch (Exception ex2) {
                log.error("RetrieveHierarchyProcess batch {} failed to set failed state: {}", batchId,
                        ex2.getMessage(), ex2);
            }
        } finally {
            if (!pool.isShutdown()) {
                pool.shutdown();
            }
        }
    }

    private void expandHierarchy(
            KrameriusObjectMetadata root,
            String instance,
            BlockingQueue<KrameriusObjectMetadata> queue,
            Batch batch) {
        Queue<KrameriusObjectMetadata> bfs = new LinkedList<>();
        bfs.offer(root);

        // Set the estimated item count to the number of pages in the hierarchy first
        // Update it as we discover new items
        int estimatedItemCount = krameriusService.getPagesCount(root.getPid(), instance) + 1;
        batchService.setEstimatedItemCount(batch, estimatedItemCount);

        int discovered = 0;

        while (!bfs.isEmpty()) {
            KrameriusObjectMetadata node = bfs.poll();

            for (KrameriusObjectMetadata child : krameriusService.getChildrenMetadata(node.getPid(), instance)) {

                Model model = Model.fromModelName(child.getModel());
                if (model == null || model.shouldIgnoreForHierarchyRetrieval()) {
                    continue;
                }

                discovered++;
                if (discovered > estimatedItemCount && discovered % PROGRESS_EVERY_N_ITEMS == 0) {
                    batchService.setEstimatedItemCount(batch, discovered);
                }

                if (!Model.PAGE.isModel(child.getModel())) {
                    bfs.offer(child);
                }

                try {
                    queue.put(child);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void processOriginItem(KrameriusObjectMetadata m, String instance) {
        DigitalObject obj = objectHierarchyService.fetchAndStore(m.getPid(), instance);

        if (Model.PAGE.isModel(m.getModel())) {
            byte[] altoBytes = krameriusService.getAltoBytes(obj.getPid(), instance);
            if (altoBytes != null) {
                altoVersionService.updateOrCreateKrameriusVersion(obj.getPid(), krameriusUserId, altoBytes);
            }
        }
    }

    private void processItem(KrameriusObjectMetadata m, String instance, Batch batch, AtomicInteger processed) {
        DigitalObject obj = objectHierarchyService.store(m);

        if (Model.PAGE.isModel(m.getModel())) {
            byte[] altoBytes = krameriusService.getAltoBytes(obj.getPid(), instance);
            if (altoBytes != null) {
                altoVersionService.updateOrCreateKrameriusVersion(obj.getPid(), krameriusUserId, altoBytes);
            }
        }

        processed.incrementAndGet();
        if (processed.get() % PROGRESS_EVERY_N_ITEMS == 0) {
            batchService.setProcessedItemCount(batch, processed.get());
        }
    }

}