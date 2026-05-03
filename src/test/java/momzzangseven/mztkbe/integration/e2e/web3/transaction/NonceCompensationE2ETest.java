package momzzangseven.mztkbe.integration.e2e.web3.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.service.ReservedNonceCompensator;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * E2E reproducer for the partial-unique-index collision the atomic compensation fix prevents.
 *
 * <p>Pre-fix: {@link ReservedNonceCompensator#compensate} (then named {@code
 * releaseReservedNonceQuietly} in {@code TransactionIssuerWorker}) only rolled back the {@code
 * web3_nonce_state.next_nonce} cursor and left the failed row's {@code web3_transactions.nonce}
 * populated. The next {@code assignNonce(N)} on a sibling row tripped the partial unique index
 * {@code uk_web3_tx_sender_nonce ON web3_transactions(from_address, nonce) WHERE nonce IS NOT
 * NULL}.
 *
 * <p>Post-fix: the compensator clears the row's nonce + stamps the terminal failure reason inside
 * the same transaction as the cursor rollback, so a sibling reserver can take {@code N} again
 * without colliding.
 */
@DisplayName("[E2E] Reserved-nonce compensation prevents uk_web3_tx_sender_nonce collision")
class NonceCompensationE2ETest extends E2ETestBase {

  private static final String FROM_ADDRESS = "0x" + "9".repeat(40);
  private static final String TO_ADDRESS = "0x" + "d".repeat(40);
  private static final long RESERVED_NONCE = 7L;

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ReservedNonceCompensator compensator;
  @Autowired private UpdateTransactionPort updateTransactionPort;

  @Test
  @DisplayName(
      "compensate clears row.nonce + rolls back cursor; sibling assignNonce(N) succeeds without"
          + " uk_web3_tx_sender_nonce violation")
  void compensate_clearsRowAndCursor_letsSiblingReassignSameNonce() {
    long failedTxId = insertCreatedTransaction("idem-failed", "ref-failed", RESERVED_NONCE);
    insertNonceState(RESERVED_NONCE + 1);

    compensator.compensate(
        failedTxId, FROM_ADDRESS, RESERVED_NONCE, Web3TxFailureReason.KMS_SIGN_FAILED_TERMINAL);

    Long failedRowNonce =
        jdbcTemplate.queryForObject(
            "SELECT nonce FROM web3_transactions WHERE id = ?", Long.class, failedTxId);
    String failedRowFailureReason =
        jdbcTemplate.queryForObject(
            "SELECT failure_reason FROM web3_transactions WHERE id = ?", String.class, failedTxId);
    Object failedRowProcessingUntil =
        jdbcTemplate.queryForObject(
            "SELECT processing_until FROM web3_transactions WHERE id = ?",
            Object.class,
            failedTxId);
    Long rolledBackNextNonce =
        jdbcTemplate.queryForObject(
            "SELECT next_nonce FROM web3_nonce_state WHERE from_address = ?",
            Long.class,
            FROM_ADDRESS);

    assertThat(failedRowNonce).isNull();
    assertThat(failedRowFailureReason)
        .isEqualTo(Web3TxFailureReason.KMS_SIGN_FAILED_TERMINAL.code());
    assertThat(failedRowProcessingUntil).isNull();
    assertThat(rolledBackNextNonce).isEqualTo(RESERVED_NONCE);

    long siblingTxId = insertCreatedTransaction("idem-sibling", "ref-sibling", null);

    assertThatCode(() -> updateTransactionPort.assignNonce(siblingTxId, RESERVED_NONCE))
        .doesNotThrowAnyException();

    Long siblingNonce =
        jdbcTemplate.queryForObject(
            "SELECT nonce FROM web3_transactions WHERE id = ?", Long.class, siblingTxId);
    assertThat(siblingNonce).isEqualTo(RESERVED_NONCE);
  }

  private long insertCreatedTransaction(String idempotencyKey, String referenceId, Long nonce) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO web3_transactions"
            + " (idempotency_key, reference_type, reference_id, from_address, to_address,"
            + " amount_wei, nonce, status)"
            + " VALUES (?, 'LEVEL_UP_REWARD', ?, ?, ?, 1, ?, 'CREATED') RETURNING id",
        Long.class,
        idempotencyKey,
        referenceId,
        FROM_ADDRESS,
        TO_ADDRESS,
        nonce);
  }

  private void insertNonceState(long nextNonce) {
    jdbcTemplate.update(
        "INSERT INTO web3_nonce_state (from_address, next_nonce) VALUES (?, ?)"
            + " ON CONFLICT (from_address) DO UPDATE SET next_nonce = EXCLUDED.next_nonce",
        FROM_ADDRESS,
        nextNonce);
  }
}
