package momzzangseven.mztkbe.modules.post.domain.event;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

/**
 * Domain event published when a post is created (other modules such as {@code level} subscribe to
 * this event to perform their own side effects).
 *
 * <p>Carries only the producer's own facts. It intentionally does not reference any {@code
 * level}-module concept (e.g. {@code XpType} or idempotency keys) so that the post module stays
 * decoupled from XP rewards.
 *
 * @param userId the post author's id
 * @param postId the persisted post's id
 * @param type the board type (FREE or QUESTION)
 * @param occurredAt the moment the post was created, in the application time zone
 */
public record PostCreatedEvent(Long userId, Long postId, PostType type, LocalDateTime occurredAt) {}
