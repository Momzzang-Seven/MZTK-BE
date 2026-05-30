package momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpCommand;
import momzzangseven.mztkbe.modules.level.application.dto.PendingXpGrant;
import momzzangseven.mztkbe.modules.level.domain.vo.XpGrantOutboxStatus;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;

/**
 * Persistence model for the XP-grant outbox.
 *
 * <p>A row is enqueued when a synchronous grant fails; the reconciliation scheduler retries it. The
 * mutation helpers ({@link #markDone()}, {@link #recordFailure}) are reliability plumbing, not
 * business rules, so they live on the entity rather than a separate domain model.
 */
@Entity
@Table(
    name = "xp_grant_outbox",
    indexes = {@Index(columnList = "status,next_attempt_at")},
    uniqueConstraints = {@UniqueConstraint(columnNames = {"idempotency_key"})})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class XpGrantOutboxEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "xp_type", nullable = false, length = 20)
  private XpType xpType;

  @Column(name = "occurred_at", nullable = false)
  private LocalDateTime occurredAt;

  @Column(name = "idempotency_key", nullable = false, length = 200)
  private String idempotencyKey;

  @Column(name = "source_ref", length = 255)
  private String sourceRef;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private XpGrantOutboxStatus status;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "next_attempt_at", nullable = false)
  private LocalDateTime nextAttemptAt;

  @Column(name = "last_error", columnDefinition = "TEXT")
  private String lastError;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  /** Builds a fresh PENDING row from a failed grant command. */
  public static XpGrantOutboxEntity pendingFrom(GrantXpCommand command, LocalDateTime now) {
    return XpGrantOutboxEntity.builder()
        .userId(command.userId())
        .xpType(command.xpType())
        .occurredAt(command.occurredAt())
        .idempotencyKey(command.idempotencyKey())
        .sourceRef(command.sourceRef())
        .status(XpGrantOutboxStatus.PENDING)
        .attemptCount(0)
        .nextAttemptAt(now)
        .build();
  }

  /** Application-layer view (id + reconstructed command) for the reconciler. */
  public PendingXpGrant toPending() {
    return new PendingXpGrant(
        id, GrantXpCommand.of(userId, xpType, occurredAt, idempotencyKey, sourceRef), attemptCount);
  }

  /** Marks the grant as applied — terminal success. */
  public void markDone() {
    this.status = XpGrantOutboxStatus.DONE;
    this.lastError = null;
  }

  /**
   * Records a failed attempt: increments the counter and either schedules a linear-backoff retry or
   * marks the row FAILED once the retry budget is exhausted. No-op on a non-PENDING row so a late
   * failure can never revert a terminal DONE/FAILED state (callers must also re-lock under FOR
   * UPDATE to make the read-modify-write atomic).
   */
  public void recordFailure(int maxAttempts, int backoffSeconds, String error, LocalDateTime now) {
    if (this.status != XpGrantOutboxStatus.PENDING) {
      return;
    }
    this.attemptCount += 1;
    this.lastError = error;
    if (this.attemptCount >= maxAttempts) {
      this.status = XpGrantOutboxStatus.FAILED;
    } else {
      this.nextAttemptAt = now.plusSeconds((long) backoffSeconds * this.attemptCount);
    }
  }

  @PrePersist
  protected void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    if (this.createdAt == null) {
      this.createdAt = now;
    }
    if (this.updatedAt == null) {
      this.updatedAt = now;
    }
    if (this.nextAttemptAt == null) {
      this.nextAttemptAt = now;
    }
    if (this.status == null) {
      this.status = XpGrantOutboxStatus.PENDING;
    }
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = LocalDateTime.now();
  }
}
