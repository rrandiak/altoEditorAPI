-- Run this script with psql against each existing PostgreSQL database before
-- deploying the matching app image.
-- Wait for other batches to finish and stop the current reindex first. Run with psql
-- without --single-transaction: CREATE/DROP INDEX CONCURRENTLY cannot run in a transaction.
-- The app currently uses Hibernate ddl-auto=update, which cannot create this index
-- concurrently. A fresh database gets the index from the AltoVersion mapping.
\set ON_ERROR_STOP on

CREATE INDEX CONCURRENTLY idx_av_present_in_instances_av_id
    ON public.alto_version_present_in_instances (alto_version_id);

-- These two indexes are redundant with object_hierarchy_pkey (uuid) and
-- idxcrq0qdah2pr5mfqj7iuu5tkq8 (parent_uuid, model), respectively.
DROP INDEX CONCURRENTLY IF EXISTS public.idx5cx7i2gug501l5muig973p9u3;
DROP INDEX CONCURRENTLY IF EXISTS public.idxdr9gtc61dyda8axwf7blfvsje;

ANALYZE public.alto_version_present_in_instances;
ANALYZE public.object_hierarchy;

-- Confirm the new index is valid before starting a new reindex batch.
SELECT i.relname AS index_name, x.indisvalid
FROM pg_index x
JOIN pg_class i ON i.oid = x.indexrelid
WHERE i.relname = 'idx_av_present_in_instances_av_id';
