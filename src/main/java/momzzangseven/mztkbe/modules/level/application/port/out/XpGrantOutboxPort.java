package momzzangseven.mztkbe.modules.level.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpCommand;
import momzzangseven.mztkbe.modules.level.application.dto.PendingXpGrant;

/**
 * Durable queue for XP grants that must eventually be applied even if the synchronous grant fails.
 */
public interface XpGrantOutboxPort {

  /**
   * Enqueues a failed grant as PENDING. Idempotency-key collisions are ignored (already queued).
   */
  void enqueue(GrantXpCommand command);

  /** Reads PENDING rows whose retry time has arrived, oldest first. Read-only (no locking). */
  List<PendingXpGrant> findDueBatch(LocalDateTime now, int limit);

  /**
   * Locks a single PENDING row whose retry time has arrived ({@code next_attempt_at <= now}) for
   * processing using {@code FOR UPDATE SKIP LOCKED}, returning empty if it is already taken, no
   * longer PENDING, or not yet due. The due-time recheck stops a stale due-batch from re-claiming a
   * row whose backoff was pushed forward by an earlier failure. Must run inside the caller's
   * transaction.
   */
  Optional<PendingXpGrant> claimForProcessing(Long id, LocalDateTime now);

  /** Marks a row DONE (terminal success). Joins the caller's transaction. */
  void markDone(Long id);

  /** Records a failed attempt — schedules a backoff retry or marks FAILED when budget is spent. */
  void recordFailure(Long id, int maxAttempts, int backoffSeconds, String error);
}
