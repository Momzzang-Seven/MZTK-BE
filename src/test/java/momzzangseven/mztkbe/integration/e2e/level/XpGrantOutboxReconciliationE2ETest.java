package momzzangseven.mztkbe.integration.e2e.level;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpCommand;
import momzzangseven.mztkbe.modules.level.application.dto.RunXpGrantReconciliationCommand;
import momzzangseven.mztkbe.modules.level.application.dto.RunXpGrantReconciliationResult;
import momzzangseven.mztkbe.modules.level.application.port.in.RunXpGrantReconciliationUseCase;
import momzzangseven.mztkbe.modules.level.application.port.out.XpGrantOutboxPort;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Proves the guaranteed-delivery contract: a grant that failed synchronously and was enqueued to
 * the outbox is eventually applied (idempotently) by the reconciler, which then marks the row DONE.
 */
@DisplayName("[E2E] MOM-465 XP 적립 outbox 재처리 보장")
class XpGrantOutboxReconciliationE2ETest extends E2ETestBase {

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private XpGrantOutboxPort outboxPort;
  @Autowired private RunXpGrantReconciliationUseCase reconciliationUseCase;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  @Test
  @DisplayName("PENDING outbox row -> 재처리 후 xp_ledger 적립 + row DONE")
  void reconcilesPendingOutboxRowAndGrantsXp() {
    TestUser user = signupAndLogin("xp-outbox-e2e-user");
    long syntheticPostId = 987654L;
    String idempotencyKey = "post:create:" + syntheticPostId;

    GrantXpCommand command =
        GrantXpCommand.of(
            user.userId(),
            XpType.POST,
            LocalDateTime.now(),
            idempotencyKey,
            "post-creation:" + syntheticPostId);

    outboxPort.enqueue(command);

    assertThat(outboxStatus(idempotencyKey)).isEqualTo("PENDING");
    assertThat(countXpLedger(user.userId(), idempotencyKey)).isZero();

    RunXpGrantReconciliationResult result =
        reconciliationUseCase.run(new RunXpGrantReconciliationCommand(10, 10, 60));

    assertThat(result.granted()).isEqualTo(1);
    assertThat(result.failed()).isZero();
    assertThat(countXpLedger(user.userId(), idempotencyKey))
        .as("재처리로 멱등 grant 가 실제 적립되었는지")
        .isEqualTo(1);
    assertThat(outboxStatus(idempotencyKey)).isEqualTo("DONE");
  }

  @Test
  @DisplayName("grant 지속 실패 -> 재시도 소진 후 outbox FAILED, ledger 미적립")
  void exhaustsRetriesAndMarksFailed() {
    TestUser user = signupAndLogin("xp-outbox-fail-e2e-user");
    long syntheticPostId = 123456L;
    String idempotencyKey = "post:create:" + syntheticPostId;

    // occurredAt precedes every XP policy's effective_from (2000-01-01), so GrantXpService throws
    // "XP policy not found" before any ledger write — a deterministic grant failure.
    GrantXpCommand command =
        GrantXpCommand.of(
            user.userId(),
            XpType.POST,
            LocalDateTime.of(1990, 1, 1, 0, 0),
            idempotencyKey,
            "post-creation:" + syntheticPostId);

    outboxPort.enqueue(command);
    assertThat(outboxStatus(idempotencyKey)).isEqualTo("PENDING");

    // maxAttempts=1 -> a single failed attempt exhausts the retry budget and transitions to FAILED.
    RunXpGrantReconciliationResult result =
        reconciliationUseCase.run(new RunXpGrantReconciliationCommand(10, 1, 60));

    assertThat(result.failed()).isEqualTo(1);
    assertThat(result.granted()).isZero();
    assertThat(countXpLedger(user.userId(), idempotencyKey))
        .as("실패한 grant 는 ledger 에 적립되면 안 됨")
        .isZero();
    assertThat(outboxStatus(idempotencyKey)).isEqualTo("FAILED");
  }

  private String outboxStatus(String idempotencyKey) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM xp_grant_outbox WHERE idempotency_key = ?",
        String.class,
        idempotencyKey);
  }

  private int countXpLedger(Long userId, String idempotencyKey) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM xp_ledger WHERE user_id = ? AND idempotency_key = ?",
            Integer.class,
            userId,
            idempotencyKey);
    return count == null ? 0 : count;
  }
}
