-- Allow reference_type to be null so that images can be unlinked from their owner
-- without physically deleting the row (prevents race conditions with Lambda callbacks).
ALTER TABLE images ALTER COLUMN reference_type DROP NOT NULL;

-- Partial index for efficient lookup by reference (used in findImagesByReference and unlinkBy*).
-- Excludes unlinked rows (reference_id IS NULL) to keep the index compact.
CREATE INDEX IF NOT EXISTS idx_images_reference
    ON images (reference_type, reference_id)
    WHERE reference_id IS NOT NULL;