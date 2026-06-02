package momzzangseven.mztkbe.integration.e2e.web3.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionWorkPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@TestPropertySource(
    properties = {
      "web3.reward-token.enabled=true",
      "web3.eip7702.enabled=false",
      "web3.reward-token.treasury.provisioning.enabled=false"
    })
@DisplayName("[E2E] Web3 terminal transaction requeue admin API")
class Web3TransactionRequeueE2ETest extends E2ETestBase {

  private static final long CHAIN_ID = 84532L;
  private static final String FROM_ADDRESS = "0x" + "a".repeat(40);
  private static final String TO_ADDRESS = "0x" + "b".repeat(40);
  private static final String ACTION_REQUEUE = "TRANSACTION_REQUEUE";
  private static final String ACTION_REQUEUE_BULK = "TRANSACTION_REQUEUE_BULK";

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private LoadTransactionWorkPort loadTransactionWorkPort;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionReceiptWorker
      txTransactionReceiptWorker;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionIssuerWorker
      txTransactionIssuerWorker;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .SignedRecoveryWorker
      txSignedRecoveryWorker;

  @Test
  @DisplayName("[E2E-1] Admin can requeue one terminal CREATED transaction and worker claims it")
  void adminCanRequeueSingleTerminalTransactionAndWorkerCanClaim() throws Exception {
    TestAdmin admin = createAdminAndLogin();
    Long txId = seedTerminalCreatedTransaction("KMS_DESCRIBE_TERMINAL");

    ResponseEntity<String> response =
        postWithBearer(
            "/admin/web3/transactions/" + txId + "/requeue",
            admin.accessToken(),
            Map.of("reason", "IAM restored", "evidence", "ops-ticket-1234"));

    assertThat(response.getStatusCode())
        .withFailMessage("response body=%s", response.getBody())
        .isEqualTo(HttpStatus.OK);
    JsonNode data = objectMapper.readTree(response.getBody()).path("data");
    assertThat(data.at("/transactionId").asLong()).isEqualTo(txId);
    assertThat(data.at("/status").asText()).isEqualTo("CREATED");
    assertThat(data.at("/originalFailureReason").asText()).isEqualTo("KMS_DESCRIBE_TERMINAL");
    assertThat(data.at("/requeued").asBoolean()).isTrue();

    assertRequeuedRow(txId);
    assertAdminAuditCount(ACTION_REQUEUE, String.valueOf(txId), admin.userId(), 1);
    assertTransactionAudit(txId, "REQUEUED", "KMS_DESCRIBE_TERMINAL");

    List<LoadTransactionWorkPort.TransactionWorkItem> claimed =
        loadTransactionWorkPort.claimByStatus(
            Web3TxStatus.CREATED, 10, "e2e-requeue-worker", Duration.ofMinutes(1));
    assertThat(claimed)
        .extracting(LoadTransactionWorkPort.TransactionWorkItem::transactionId)
        .contains(txId);
    assertThat(claimed.stream().filter(item -> item.transactionId().equals(txId)).findFirst())
        .hasValueSatisfying(item -> assertThat(item.failureReason()).isNull());
  }

  @Test
  @DisplayName("[E2E-1B] Admin can requeue a terminal transaction with a dropped nonce reservation")
  void adminCanRequeueTerminalTransactionWithDroppedNonceReservationAndWorkerCanClaim()
      throws Exception {
    TestAdmin admin = createAdminAndLogin();
    SeededDroppedNonceTransaction seeded =
        seedTerminalCreatedTransactionWithDroppedNonce("KMS_SIGN_FAILED_TERMINAL");

    ResponseEntity<String> response =
        postWithBearer(
            "/admin/web3/transactions/" + seeded.transactionId() + "/requeue",
            admin.accessToken(),
            Map.of("reason", "KMS access restored", "evidence", "ops-ticket-2222"));

    assertThat(response.getStatusCode())
        .withFailMessage("response body=%s", response.getBody())
        .isEqualTo(HttpStatus.OK);
    JsonNode data = objectMapper.readTree(response.getBody()).path("data");
    assertThat(data.at("/transactionId").asLong()).isEqualTo(seeded.transactionId());
    assertThat(data.at("/originalFailureReason").asText()).isEqualTo("KMS_SIGN_FAILED_TERMINAL");
    assertThat(data.at("/requeued").asBoolean()).isTrue();

    assertRequeuedRow(seeded.transactionId());
    assertTransactionNonce(seeded.transactionId(), seeded.nonce());
    assertNonceSlotReactivated(seeded);
    assertAdminAuditCount(
        ACTION_REQUEUE, String.valueOf(seeded.transactionId()), admin.userId(), 1);
    assertTransactionAudit(seeded.transactionId(), "REQUEUED", "KMS_SIGN_FAILED_TERMINAL");

    List<LoadTransactionWorkPort.TransactionWorkItem> claimed =
        loadTransactionWorkPort.claimByStatus(
            Web3TxStatus.CREATED, 10, "e2e-requeue-dropped-nonce-worker", Duration.ofMinutes(1));
    assertThat(claimed)
        .extracting(LoadTransactionWorkPort.TransactionWorkItem::transactionId)
        .contains(seeded.transactionId());
    assertThat(
            claimed.stream()
                .filter(item -> item.transactionId().equals(seeded.transactionId()))
                .findFirst())
        .hasValueSatisfying(
            item -> {
              assertThat(item.failureReason()).isNull();
              assertThat(item.nonce()).isEqualTo(seeded.nonce());
            });
  }

  @Test
  @DisplayName("[E2E-2] Admin can bulk requeue and receives per-id outcomes")
  void adminCanBulkRequeueTerminalTransactions() throws Exception {
    TestAdmin admin = createAdminAndLogin();
    Long first = seedTerminalCreatedTransaction("KMS_DESCRIBE_TERMINAL");
    Long second = seedTerminalCreatedTransaction("TREASURY_TOKEN_INSUFFICIENT");
    Long rejected = seedTerminalCreatedTransaction("INVALID_SIGNED_TX");
    Long missing = rejected + 1_000_000L;

    ResponseEntity<String> response =
        postWithBearer(
            "/admin/web3/transactions/requeue",
            admin.accessToken(),
            Map.of(
                "transactionIds", List.of(first, second, rejected, missing, first),
                "reason", "treasury repaired",
                "evidence", "ops-ticket-5678"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode data = objectMapper.readTree(response.getBody()).path("data");
    assertThat(data.at("/requested").asInt()).isEqualTo(5);
    assertThat(data.at("/unique").asInt()).isEqualTo(4);
    assertThat(data.at("/succeeded").asInt()).isEqualTo(2);
    assertThat(data.at("/rejected").asInt()).isEqualTo(1);
    assertThat(data.at("/notFound").asInt()).isEqualTo(1);

    assertItemResult(data.path("items"), first, "REQUEUED");
    assertItemResult(data.path("items"), second, "REQUEUED");
    assertItemResult(data.path("items"), rejected, "REJECTED");
    assertItemResult(data.path("items"), missing, "NOT_FOUND");

    assertRequeuedRow(first);
    assertRequeuedRow(second);
    assertFailureReason(rejected, "INVALID_SIGNED_TX");
    assertAdminAuditCount(ACTION_REQUEUE_BULK, "BULK", admin.userId(), 1);
    assertTransactionAudit(first, "REQUEUED", "KMS_DESCRIBE_TERMINAL");
    assertTransactionAudit(second, "REQUEUED", "TREASURY_TOKEN_INSUFFICIENT");
    assertTransactionAudit(rejected, "REJECTED", "INVALID_SIGNED_TX");
  }

  @Test
  @DisplayName("[E2E-3] Admin can filter terminal transactions by failureReason")
  void adminCanFilterTransactionsByFailureReason() throws Exception {
    TestAdmin admin = createAdminAndLogin();
    Long matching = seedTerminalCreatedTransaction("KMS_DESCRIBE_TERMINAL");
    Long other = seedTerminalCreatedTransaction("TREASURY_TOKEN_INSUFFICIENT");

    ResponseEntity<String> response =
        getWithBearer(
            "/admin/web3/transactions?failureReason=KMS_DESCRIBE_TERMINAL", admin.accessToken());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode content = objectMapper.readTree(response.getBody()).path("data").path("content");
    assertThat(findItem(content, matching).at("/failureReason").asText())
        .isEqualTo("KMS_DESCRIBE_TERMINAL");
    assertThat(findItem(content, other).isMissingNode()).isTrue();
  }

  @Test
  @DisplayName("[E2E-4] Non-admin cannot requeue and no admin audit row is written")
  void nonAdminCannotRequeue() throws Exception {
    TestUser user = signupAndLogin("RequeueUser");
    Long txId = seedTerminalCreatedTransaction("KMS_DESCRIBE_TERMINAL");

    ResponseEntity<String> response =
        postWithBearer(
            "/admin/web3/transactions/" + txId + "/requeue",
            user.accessToken(),
            Map.of("reason", "forbidden", "evidence", "ops"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertFailureReason(txId, "KMS_DESCRIBE_TERMINAL");
    assertAdminAuditCount(ACTION_REQUEUE, String.valueOf(txId), user.userId(), 0);
  }

  private TestAdmin createAdminAndLogin() throws Exception {
    String email = "admin-" + uniqueToken() + "@internal.mztk.local";
    jdbcTemplate.update(
        "INSERT INTO users (email, role, nickname, created_at, updated_at) "
            + "VALUES (?, 'ADMIN_GENERATED', 'TxRequeueAdmin', NOW(), NOW())",
        email);
    Long userId = userIdByEmail(email);
    String loginId = String.valueOf(10000000 + Math.abs(UUID.randomUUID().hashCode() % 90000000));
    String password = "AdminP@ss" + uniqueToken().substring(0, 8);
    jdbcTemplate.update(
        "INSERT INTO admin_accounts (user_id, login_id, password_hash, created_by, "
            + "last_login_at, password_last_rotated_at, deleted_at, created_at, updated_at) "
            + "VALUES (?, ?, ?, NULL, NULL, NULL, NULL, NOW(), NOW())",
        userId,
        loginId,
        passwordEncoder.encode(password));
    return new TestAdmin(userId, loginAdmin(loginId, password));
  }

  private String loginAdmin(String loginId, String password) throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/auth/login",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("provider", "LOCAL_ADMIN", "loginId", loginId, "password", password),
                jsonOnlyHeaders()),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return objectMapper.readTree(response.getBody()).at("/data/accessToken").asText();
  }

  private Long seedTerminalCreatedTransaction(String failureReason) {
    return seedTerminalCreatedTransaction(failureReason, null);
  }

  private Long seedTerminalCreatedTransaction(String failureReason, Long nonce) {
    String idempotencyKey = "e2e:tx-requeue:" + uniqueToken();
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO web3_transactions (
            idempotency_key, reference_type, reference_id, from_address, to_address,
            amount_wei, tx_type, nonce, chain_id, status, failure_reason, processing_by,
            processing_until, created_at, updated_at
        )
        VALUES (?, 'LEVEL_UP_REWARD', ?, ?, ?, ?, 'EIP1559', ?, ?, 'CREATED', ?,
            'issuer-worker', NOW() + INTERVAL '10 minutes', NOW(), NOW())
        RETURNING id
        """,
        Long.class,
        idempotencyKey,
        "reward-" + idempotencyKey,
        FROM_ADDRESS,
        TO_ADDRESS,
        BigInteger.valueOf(1_000_000L),
        nonce,
        CHAIN_ID,
        failureReason);
  }

  private SeededDroppedNonceTransaction seedTerminalCreatedTransactionWithDroppedNonce(
      String failureReason) {
    long nonce = randomNonce();
    Long txId = seedTerminalCreatedTransaction(failureReason, nonce);
    String idempotencyKey = "e2e:tx-requeue-attempt:" + uniqueToken();
    Long attemptId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO web3_nonce_slot_attempts (
                chain_id, from_address, nonce, attempt_no, tx_id, status,
                idempotency_key, terminal_reason, created_at, updated_at
            )
            VALUES (?, ?, ?, 1, ?, 'DROPPED', ?, ?, NOW(), NOW())
            RETURNING id
            """,
            Long.class,
            CHAIN_ID,
            FROM_ADDRESS,
            nonce,
            txId,
            idempotencyKey,
            failureReason);

    jdbcTemplate.update(
        """
        INSERT INTO web3_nonce_slots (
            chain_id, from_address, nonce, status, attempt_no, released_attempt_id,
            released_tx_id, released_at, release_reason, replacement_prepare_attempt_count,
            broadcast_recovery_attempt_count, created_at, updated_at
        )
        VALUES (?, ?, ?, 'DROPPED', 1, ?, ?, NOW(), ?, 0, 0, NOW(), NOW())
        """,
        CHAIN_ID,
        FROM_ADDRESS,
        nonce,
        attemptId,
        txId,
        failureReason);
    return new SeededDroppedNonceTransaction(txId, nonce);
  }

  private ResponseEntity<String> getWithBearer(String path, String accessToken) {
    return restTemplate.exchange(
        baseUrl() + path,
        HttpMethod.GET,
        new HttpEntity<>(bearerJsonHeaders(accessToken)),
        String.class);
  }

  private ResponseEntity<String> postWithBearer(
      String path, String accessToken, Map<String, ?> body) {
    return restTemplate.exchange(
        baseUrl() + path,
        HttpMethod.POST,
        new HttpEntity<>(body, bearerJsonHeaders(accessToken)),
        String.class);
  }

  private void assertRequeuedRow(Long txId) {
    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT status, failure_reason, processing_by, processing_until "
                + "FROM web3_transactions WHERE id = ?",
            txId);
    assertThat(row.get("status")).isEqualTo("CREATED");
    assertThat(row.get("failure_reason")).isNull();
    assertThat(row.get("processing_by")).isNull();
    assertThat(row.get("processing_until")).isNull();
  }

  private void assertTransactionNonce(Long txId, Long expectedNonce) {
    Long actualNonce =
        jdbcTemplate.queryForObject(
            "SELECT nonce FROM web3_transactions WHERE id = ?", Long.class, txId);
    assertThat(actualNonce).isEqualTo(expectedNonce);
  }

  private void assertNonceSlotReactivated(SeededDroppedNonceTransaction seeded) {
    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            """
            SELECT status, active_tx_id, released_tx_id, release_reason
            FROM web3_nonce_slots
            WHERE chain_id = ? AND from_address = ? AND nonce = ?
            """,
            CHAIN_ID,
            FROM_ADDRESS,
            seeded.nonce());
    assertThat(row.get("status")).isEqualTo("RESERVED");
    assertThat(((Number) row.get("active_tx_id")).longValue()).isEqualTo(seeded.transactionId());
    assertThat(row.get("released_tx_id")).isNull();
    assertThat(row.get("release_reason")).isNull();
  }

  private void assertFailureReason(Long txId, String failureReason) {
    String actual =
        jdbcTemplate.queryForObject(
            "SELECT failure_reason FROM web3_transactions WHERE id = ?", String.class, txId);
    assertThat(actual).isEqualTo(failureReason);
  }

  private void assertAdminAuditCount(
      String actionType, String targetId, Long operatorId, int expectedCount) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM admin_action_audits "
                + "WHERE action_type = ? AND target_id = ? AND operator_id = ?",
            Integer.class,
            actionType,
            targetId,
            operatorId);
    assertThat(count).isEqualTo(expectedCount);
  }

  private void assertTransactionAudit(Long txId, String result, String originalFailureReason)
      throws Exception {
    String detailJson =
        jdbcTemplate.queryForObject(
            """
            SELECT detail_json
            FROM web3_transaction_audits
            WHERE web3_transaction_id = ? AND event_type = 'REQUEUE'
            ORDER BY id DESC
            LIMIT 1
            """,
            String.class,
            txId);
    JsonNode detail = objectMapper.readTree(detailJson);
    assertThat(detail.at("/result").asText()).isEqualTo(result);
    assertThat(detail.at("/originalFailureReason").asText()).isEqualTo(originalFailureReason);
  }

  private void assertItemResult(JsonNode items, Long txId, String expectedResult) {
    assertThat(findItem(items, txId).at("/result").asText()).isEqualTo(expectedResult);
  }

  private JsonNode findItem(JsonNode items, Long txId) {
    for (JsonNode item : items) {
      if (item.at("/transactionId").asLong() == txId) {
        return item;
      }
    }
    return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
  }

  private Long userIdByEmail(String email) {
    return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
  }

  private static String uniqueToken() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  private static long randomNonce() {
    return 10_000_000L + Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1_000_000L);
  }

  private record TestAdmin(Long userId, String accessToken) {}

  private record SeededDroppedNonceTransaction(Long transactionId, Long nonce) {}
}
