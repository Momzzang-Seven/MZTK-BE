-- Answer comments need the root question post id for activity, visibility, and audit flows even
-- after the answer row is physically deleted. Backfill all recoverable rows before tightening the
-- target shape.

UPDATE comments
SET post_id = (
    SELECT a.post_id
    FROM answers a
    WHERE a.id = comments.answer_id
)
WHERE target_type = 'ANSWER'
  AND post_id IS NULL
  AND EXISTS (
      SELECT 1
      FROM answers a
      WHERE a.id = comments.answer_id
  );

-- Some QnA answer projections can outlive the local answers row and still carry the root post id.
UPDATE comments
SET post_id = (
    SELECT qa.post_id
    FROM web3_qna_answers qa
    WHERE qa.answer_id = comments.answer_id
)
WHERE target_type = 'ANSWER'
  AND post_id IS NULL
  AND EXISTS (
      SELECT 1
      FROM web3_qna_answers qa
      WHERE qa.answer_id = comments.answer_id
  );

-- The strict target constraint applies to every row, including soft-deleted rows. Legacy answer
-- comments whose answer/post context cannot be restored would otherwise require a fake post_id, so
-- remove them after marking them deleted instead of preserving misleading data.
UPDATE comments
SET is_deleted = true,
    updated_at = CURRENT_TIMESTAMP
WHERE target_type = 'ANSWER'
  AND post_id IS NULL;

DELETE FROM comments
WHERE parent_id IN (
    SELECT id
    FROM comments
    WHERE target_type = 'ANSWER'
      AND post_id IS NULL
);

DELETE FROM comments
WHERE target_type = 'ANSWER'
  AND post_id IS NULL;

ALTER TABLE comments
    DROP CONSTRAINT IF EXISTS chk_comments_target;

ALTER TABLE comments
    ADD CONSTRAINT chk_comments_target
        CHECK (
            (target_type = 'POST' AND post_id IS NOT NULL AND answer_id IS NULL)
            OR (target_type = 'ANSWER' AND post_id IS NOT NULL AND answer_id IS NOT NULL)
        );
