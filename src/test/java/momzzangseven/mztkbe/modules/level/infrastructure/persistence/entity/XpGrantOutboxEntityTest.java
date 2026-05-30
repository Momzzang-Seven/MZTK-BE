package momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpCommand;
import momzzangseven.mztkbe.modules.level.application.dto.PendingXpGrant;
import momzzangseven.mztkbe.modules.level.domain.vo.XpGrantOutboxStatus;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("XpGrantOutboxEntity unit test")
class XpGrantOutboxEntityTest {

  private static final LocalDateTime ENQUEUED_AT = LocalDateTime.of(2026, 5, 29, 10, 0);

  private GrantXpCommand postCommand() {
    return GrantXpCommand.of(
        7L,
        XpType.POST,
        LocalDateTime.of(2026, 5, 29, 9, 30),
        "post:create:42",
        "post-creation:42");
  }

  @Test
  @DisplayName("pendingFrom -> toPending preserves all command fields and starts PENDING")
  void pendingFromAndToPending_roundtripsFields() {
    GrantXpCommand command = postCommand();

    XpGrantOutboxEntity entity = XpGrantOutboxEntity.pendingFrom(command, ENQUEUED_AT);

    assertThat(entity.getStatus()).isEqualTo(XpGrantOutboxStatus.PENDING);
    assertThat(entity.getAttemptCount()).isZero();
    assertThat(entity.getNextAttemptAt()).isEqualTo(ENQUEUED_AT);

    PendingXpGrant pending = entity.toPending();
    assertThat(pending.attemptCount()).isZero();
    assertThat(pending.command().userId()).isEqualTo(command.userId());
    assertThat(pending.command().xpType()).isEqualTo(command.xpType());
    assertThat(pending.command().occurredAt()).isEqualTo(command.occurredAt());
    assertThat(pending.command().idempotencyKey()).isEqualTo(command.idempotencyKey());
    assertThat(pending.command().sourceRef()).isEqualTo(command.sourceRef());
  }

  @Test
  @DisplayName("recordFailure below max attempts keeps PENDING and schedules linear backoff")
  void recordFailureBelowMax_staysPendingWithLinearBackoff() {
    XpGrantOutboxEntity entity = XpGrantOutboxEntity.pendingFrom(postCommand(), ENQUEUED_AT);
    LocalDateTime firstAttemptAt = LocalDateTime.of(2026, 5, 29, 11, 0);
    LocalDateTime secondAttemptAt = LocalDateTime.of(2026, 5, 29, 12, 0);

    entity.recordFailure(3, 60, "grant unavailable", firstAttemptAt);

    assertThat(entity.getStatus()).isEqualTo(XpGrantOutboxStatus.PENDING);
    assertThat(entity.getAttemptCount()).isEqualTo(1);
    assertThat(entity.getLastError()).isEqualTo("grant unavailable");
    assertThat(entity.getNextAttemptAt()).isEqualTo(firstAttemptAt.plusSeconds(60));

    entity.recordFailure(3, 60, "still unavailable", secondAttemptAt);

    assertThat(entity.getStatus()).isEqualTo(XpGrantOutboxStatus.PENDING);
    assertThat(entity.getAttemptCount()).isEqualTo(2);
    assertThat(entity.getNextAttemptAt()).isEqualTo(secondAttemptAt.plusSeconds(120));
  }

  @Test
  @DisplayName("recordFailure at max attempts transitions to FAILED and leaves nextAttemptAt")
  void recordFailureAtMax_transitionsToFailed() {
    XpGrantOutboxEntity entity = XpGrantOutboxEntity.pendingFrom(postCommand(), ENQUEUED_AT);
    LocalDateTime attemptAt = LocalDateTime.of(2026, 5, 29, 11, 0);

    entity.recordFailure(1, 60, "policy not found", attemptAt);

    assertThat(entity.getStatus()).isEqualTo(XpGrantOutboxStatus.FAILED);
    assertThat(entity.getAttemptCount()).isEqualTo(1);
    assertThat(entity.getLastError()).isEqualTo("policy not found");
    assertThat(entity.getNextAttemptAt()).isEqualTo(ENQUEUED_AT);
  }

  @Test
  @DisplayName("recordFailure on a DONE row is a no-op (terminal state never reverts)")
  void recordFailureOnDone_isNoOp() {
    XpGrantOutboxEntity entity = XpGrantOutboxEntity.pendingFrom(postCommand(), ENQUEUED_AT);
    entity.markDone();

    entity.recordFailure(1, 60, "late failure after success", LocalDateTime.of(2026, 5, 29, 13, 0));

    assertThat(entity.getStatus()).isEqualTo(XpGrantOutboxStatus.DONE);
    assertThat(entity.getAttemptCount()).isZero();
    assertThat(entity.getLastError()).isNull();
  }

  @Test
  @DisplayName("markDone moves to DONE and clears the last error")
  void markDone_clearsErrorAndCompletes() {
    XpGrantOutboxEntity entity = XpGrantOutboxEntity.pendingFrom(postCommand(), ENQUEUED_AT);
    entity.recordFailure(3, 60, "transient", LocalDateTime.of(2026, 5, 29, 11, 0));

    entity.markDone();

    assertThat(entity.getStatus()).isEqualTo(XpGrantOutboxStatus.DONE);
    assertThat(entity.getLastError()).isNull();
  }
}
