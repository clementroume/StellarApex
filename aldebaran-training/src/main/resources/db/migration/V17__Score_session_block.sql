-- A score done within a session journal knows the block it was logged for: the lock of a session
-- block (frozen once scored) must not confuse two blocks borrowing the same WOD.
ALTER TABLE scores ADD COLUMN session_block_id BIGINT REFERENCES session_blocks (id) ON DELETE RESTRICT;
CREATE INDEX idx_scores_session_block ON scores (session_block_id);

-- Existing session scores: the first block of their session holding their WOD
UPDATE scores s
SET session_block_id = (SELECT b.id
                        FROM session_logs l
                                 JOIN session_blocks b ON b.session_id = l.session_id
                        WHERE l.id = s.session_log_id
                          AND b.wod_id = s.wod_id
                        ORDER BY b.order_index
                        LIMIT 1)
WHERE s.session_log_id IS NOT NULL;
