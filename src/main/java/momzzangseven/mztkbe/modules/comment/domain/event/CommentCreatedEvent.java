package momzzangseven.mztkbe.modules.comment.domain.event;

import java.time.LocalDateTime;

/**
 * Domain event published when a comment is created.
 *
 * <p>Carries only the producer's own facts (writer, comment id, and the moment the comment was
 * written). It intentionally does not reference any {@code level}-module concept (e.g. {@code
 * XpType} or idempotency keys) so that the comment module stays decoupled from XP rewards.
 *
 * @param userId the comment writer's id
 * @param commentId the persisted comment's id
 * @param occurredAt the moment the comment was created, in the application time zone
 */
public record CommentCreatedEvent(Long userId, Long commentId, LocalDateTime occurredAt) {}
