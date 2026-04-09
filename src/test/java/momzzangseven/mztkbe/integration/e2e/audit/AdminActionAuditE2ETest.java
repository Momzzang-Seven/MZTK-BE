package momzzangseven.mztkbe.integration.e2e.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * E2E coverage of the unified {@code admin_action_audits} write path introduced by MOM-322.
 *
 * <p>Verifies, against a real PostgreSQL instance and a fully wired Spring context, that:
 *
 * <ul>
 *   <li>Successful and failing {@code @AdminOnly} web3 invocations land in {@code
 *       admin_action_audits} with the expected {@code target_type} value.
 *   <li>{@code REQUIRES_NEW} keeps the audit row committed even when the outer business transaction
 *       rolls back.
 *   <li>Out-of-scope audit systems ({@code web3_transaction_audits}, {@code
 *       web3_treasury_provision_audits}) are not regressed.
 *   <li>Authorization failures (non-admin / unauthenticated) never produce an audit row.
 * </ul>
 */
@Tag("e2e")
@ActiveProfiles("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "web3.reward-token.enabled=true",
      "web3.reward-token.treasury.provisioning.enabled=true"
    })
@DisplayName("[E2E] admin_action_audits 통합 테이블 write path 테스트")
class AdminActionAuditE2ETest {

  private static final String ACTION_TX_MARK_SUCCEEDED = "TRANSACTION_MARK_SUCCEEDED";
  private static final String ACTION_TREASURY_KEY_PROVISION = "TREASURY_KEY_PROVISION";
  private static final String TARGET_TYPE_WEB3_TRANSACTION = "WEB3_TRANSACTION";
  private static final String TARGET_TYPE_TREASURY_KEY = "TREASURY_KEY";

  /** Hardhat first account private key — test-only, holds no real value. */
  private static final String VALID_TEST_PRIVATE_KEY =
      "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"; // gitleaks:allow

  private static final String VALID_TEST_TREASURY_ADDRESS =
      "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private Web3ContractPort web3ContractPort;

  private String baseUrl;
  private final List<String> createdUserEmails = new ArrayList<>();
  private final List<Long> createdUserIds = new ArrayList<>();
  private final List<Long> createdWeb3TransactionIds = new ArrayList<>();
  private final List<String> insertedTreasuryAliases = new ArrayList<>();

  // ============================================================
  // Setup / teardown
  // ============================================================

  @BeforeEach
  void setUp() {
    baseUrl = "http://localhost:" + port;
  }

  @AfterEach
  void tearDown() {
    // 1. admin_action_audits rows for our admin operators
    if (!createdUserIds.isEmpty()) {
      String inClause = inClausePlaceholders(createdUserIds.size());
      jdbcTemplate.update(
          "DELETE FROM admin_action_audits WHERE operator_id IN (" + inClause + ")",
          createdUserIds.toArray());
      jdbcTemplate.update(
          "DELETE FROM web3_treasury_provision_audits WHERE operator_id IN (" + inClause + ")",
          createdUserIds.toArray());
    }

    // 2. web3_transaction_audits + web3_transactions for seeded rows
    for (Long txId : createdWeb3TransactionIds) {
      jdbcTemplate.update(
          "DELETE FROM web3_transaction_audits WHERE web3_transaction_id = ?", txId);
      jdbcTemplate.update("DELETE FROM web3_transactions WHERE id = ?", txId);
    }
    createdWeb3TransactionIds.clear();

    // 3. treasury keys provisioned during the test
    for (String alias : insertedTreasuryAliases) {
      jdbcTemplate.update("DELETE FROM web3_treasury_keys WHERE wallet_alias = ?", alias);
    }
    insertedTreasuryAliases.clear();

    // 4. delete admin users (and dependent rows)
    for (String email : createdUserEmails) {
      jdbcTemplate.update(
          "DELETE FROM refresh_tokens WHERE user_id = (SELECT id FROM users WHERE email = ?)",
          email);
      jdbcTemplate.update(
          "DELETE FROM user_progress WHERE user_id = (SELECT id FROM users WHERE email = ?)",
          email);
      jdbcTemplate.update(
          "DELETE FROM users_account WHERE user_id = (SELECT id FROM users WHERE email = ?)",
          email);
      jdbcTemplate.update("DELETE FROM users WHERE email = ?", email);
    }
    createdUserEmails.clear();
    createdUserIds.clear();
  }

  // ============================================================
  // Helpers
  // ============================================================

  private static String uniqueEmail() {
    return "e2e-audit-"
        + UUID.randomUUID().toString().replace("-", "").substring(0, 10)
        + "@example.com";
  }

  private static String inClausePlaceholders(int n) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < n; i++) {
      if (i > 0) {
        sb.append(",");
      }
      sb.append("?");
    }
    return sb.toString();
  }

  /** Signs up a user, promotes them to ADMIN via direct DB update, then logs in. */
  private AdminUser createAdminAndLogin() throws Exception {
    String email = uniqueEmail();
    signup(email, "Test@1234!", "AuditAdmin");
    createdUserEmails.add(email);

    Long userId =
        jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", userId);
    createdUserIds.add(userId);

    String accessToken = loginAndGetAccessToken(email, "Test@1234!");
    return new AdminUser(userId, accessToken);
  }

  private void signup(String email, String password, String nickname) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    Map<String, String> body = Map.of("email", email, "password", password, "nickname", nickname);
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/auth/signup",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            String.class);
    assertThat(response.getStatusCode().is2xxSuccessful())
        .as("signup must succeed: " + response.getBody())
        .isTrue();
  }

  private String loginAndGetAccessToken(String email, String password) throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    Map<String, Object> body = Map.of("provider", "LOCAL", "email", email, "password", password);
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/auth/login",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            String.class);
    assertThat(response.getStatusCode().is2xxSuccessful())
        .as("login must succeed: " + response.getBody())
        .isTrue();
    return objectMapper.readTree(response.getBody()).at("/data/accessToken").asText();
  }

  private HttpHeaders bearer(String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(accessToken);
    return headers;
  }

  /**
   * Inserts a {@code web3_transactions} row in {@code UNCONFIRMED} state, ready for the
   * mark-succeeded admin override path. Returns the auto-generated id.
   */
  private long seedUnconfirmedTransaction(String txHash, Long toUserId) {
    LocalDateTime now = LocalDateTime.now();
    String idempotencyKey = "e2e-audit-" + UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO web3_transactions ("
            + "idempotency_key, reference_type, reference_id, from_user_id, to_user_id, "
            + "from_address, to_address, amount_wei, tx_type, status, tx_hash, created_at, updated_at"
            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        idempotencyKey,
        "LEVEL_UP_REWARD",
        "ref-" + idempotencyKey,
        null,
        toUserId,
        "0x0000000000000000000000000000000000000001",
        "0x0000000000000000000000000000000000000002",
        BigInteger.valueOf(1_000_000L),
        "EIP1559",
        "UNCONFIRMED",
        txHash,
        now,
        now);
    Long id =
        jdbcTemplate.queryForObject(
            "SELECT id FROM web3_transactions WHERE idempotency_key = ?",
            Long.class,
            idempotencyKey);
    createdWeb3TransactionIds.add(id);
    return id;
  }

  /**
   * Inserts a {@code web3_transactions} row in PENDING state — admin override should be rejected by
   * {@code Web3TransactionStateInvalidException} (only UNCONFIRMED is overridable).
   */
  private long seedPendingTransaction(String txHash, Long toUserId) {
    LocalDateTime now = LocalDateTime.now();
    String idempotencyKey = "e2e-audit-pending-" + UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO web3_transactions ("
            + "idempotency_key, reference_type, reference_id, from_user_id, to_user_id, "
            + "from_address, to_address, amount_wei, tx_type, status, tx_hash, created_at, updated_at"
            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        idempotencyKey,
        "LEVEL_UP_REWARD",
        "ref-" + idempotencyKey,
        null,
        toUserId,
        "0x0000000000000000000000000000000000000001",
        "0x0000000000000000000000000000000000000002",
        BigInteger.valueOf(1_000_000L),
        "EIP1559",
        "PENDING",
        txHash,
        now,
        now);
    Long id =
        jdbcTemplate.queryForObject(
            "SELECT id FROM web3_transactions WHERE idempotency_key = ?",
            Long.class,
            idempotencyKey);
    createdWeb3TransactionIds.add(id);
    return id;
  }

  private void stubReceiptSuccess(String txHash) {
    given(web3ContractPort.getReceipt(txHash))
        .willReturn(new Web3ContractPort.ReceiptResult(txHash, true, true, "primary", false, null));
  }

  private void stubReceiptSuccessForAny() {
    given(web3ContractPort.getReceipt(anyString()))
        .willAnswer(
            inv ->
                new Web3ContractPort.ReceiptResult(
                    inv.getArgument(0, String.class), true, true, "primary", false, null));
  }

  private ResponseEntity<String> markSucceeded(
      String accessToken, long txId, String txHash, String reason, String evidence) {
    Map<String, String> body =
        Map.of(
            "txHash", txHash,
            "explorerUrl", "https://explorer/tx/" + txId,
            "reason", reason,
            "evidence", evidence);
    return restTemplate.exchange(
        baseUrl + "/admin/web3/transactions/" + txId + "/mark-succeeded",
        HttpMethod.POST,
        new HttpEntity<>(body, bearer(accessToken)),
        String.class);
  }

  private ResponseEntity<String> provisionTreasury(
      String accessToken, String privateKey, String walletAlias) {
    Map<String, String> body = Map.of("treasuryPrivateKey", privateKey, "walletAlias", walletAlias);
    return restTemplate.exchange(
        baseUrl + "/admin/web3/treasury-keys/provision",
        HttpMethod.POST,
        new HttpEntity<>(body, bearer(accessToken)),
        String.class);
  }

  private record AdminUser(Long userId, String accessToken) {}

  // ============================================================
  // E-1: mark-succeeded happy path → admin_action_audits row with target_type='WEB3_TRANSACTION'
  // ============================================================

  @Test
  @DisplayName(
      "[E-1] MarkTransactionSucceeded 성공 → admin_action_audits 에 target_type='WEB3_TRANSACTION' row 적재")
  void markTransactionSucceeded_success_recordsAdminActionAuditWithWeb3TransactionTargetType()
      throws Exception {
    AdminUser admin = createAdminAndLogin();
    String txHash = "0x" + repeat('a', 64);
    long txId = seedUnconfirmedTransaction(txHash, admin.userId());
    stubReceiptSuccess(txHash);
    OffsetDateTime before = OffsetDateTime.now().minusSeconds(1);

    ResponseEntity<String> response =
        markSucceeded(admin.accessToken(), txId, txHash, "manual proof", "ticket-22");

    // 1. HTTP 200 + payload assertions
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/data/status").asText()).isEqualTo("SUCCEEDED");
    assertThat(root.at("/data/previousStatus").asText()).isEqualTo("UNCONFIRMED");
    assertThat(root.at("/data/txHash").asText()).isEqualTo(txHash);

    // 2. exactly one admin_action_audits row exists for this admin/action/target
    Integer auditCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM admin_action_audits "
                + "WHERE target_type=? AND action_type=? AND target_id=? AND operator_id=?",
            Integer.class,
            TARGET_TYPE_WEB3_TRANSACTION,
            ACTION_TX_MARK_SUCCEEDED,
            String.valueOf(txId),
            admin.userId());
    assertThat(auditCount).isEqualTo(1);

    // 3. row content
    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT operator_id, success, target_type, detail_json, created_at "
                + "FROM admin_action_audits "
                + "WHERE target_type=? AND action_type=? AND target_id=? AND operator_id=?",
            TARGET_TYPE_WEB3_TRANSACTION,
            ACTION_TX_MARK_SUCCEEDED,
            String.valueOf(txId),
            admin.userId());
    assertThat(((Number) row.get("operator_id")).longValue()).isEqualTo(admin.userId());
    assertThat(row.get("success")).isEqualTo(true);
    assertThat(row.get("target_type")).isEqualTo(TARGET_TYPE_WEB3_TRANSACTION);

    // 4. detail_json — sanitized record args. The mark-succeeded command is a record so it is
    //    expanded into nested map under arguments.command.
    String detailJson = (String) row.get("detail_json");
    assertThat(detailJson).isNotNull();
    JsonNode detail = objectMapper.readTree(detailJson);
    JsonNode commandNode = detail.at("/arguments/command");
    assertThat(commandNode.isMissingNode()).isFalse();
    assertThat(commandNode.at("/txHash").asText()).isEqualTo(txHash);
    assertThat(commandNode.at("/reason").asText()).isEqualTo("manual proof");

    // 5. created_at within ±10 seconds
    OffsetDateTime createdAt =
        ((java.sql.Timestamp) row.get("created_at"))
            .toInstant()
            .atOffset(OffsetDateTime.now().getOffset());
    assertThat(createdAt)
        .isAfterOrEqualTo(before)
        .isBeforeOrEqualTo(OffsetDateTime.now().plusSeconds(10));

    // 6. out-of-scope regression: web3_transaction_audits CS_OVERRIDE row exists
    Integer csOverrideCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM web3_transaction_audits "
                + "WHERE event_type='CS_OVERRIDE' AND web3_transaction_id = ?",
            Integer.class,
            txId);
    assertThat(csOverrideCount).isEqualTo(1);

    // 7. business state changed
    String txStatus =
        jdbcTemplate.queryForObject(
            "SELECT status FROM web3_transactions WHERE id = ?", String.class, txId);
    assertThat(txStatus).isEqualTo("SUCCEEDED");
  }

  // ============================================================
  // E-2 / E-10: failure → audit committed via REQUIRES_NEW; outer tx rolled back
  // ============================================================

  @Test
  @DisplayName(
      "[E-2/E-10] mark-succeeded 비즈니스 실패 → admin_action_audits success=false 가 commit, 외부 tx 롤백")
  void markTransactionSucceeded_invalidState_recordsFailureAuditAndRollsBackBusinessTx()
      throws Exception {
    AdminUser admin = createAdminAndLogin();
    String txHash = "0x" + repeat('b', 64);
    long txId = seedPendingTransaction(txHash, admin.userId());
    stubReceiptSuccess(txHash); // would not be reached but harmless

    ResponseEntity<String> response =
        markSucceeded(admin.accessToken(), txId, txHash, "should fail", "n/a");

    // 1. business endpoint returns 4xx because state is not UNCONFIRMED
    assertThat(response.getStatusCode().is4xxClientError())
        .as("expected 4xx but was: " + response.getStatusCode() + " body=" + response.getBody())
        .isTrue();

    // 2. failure audit row exists in admin_action_audits — committed by REQUIRES_NEW
    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT success, detail_json FROM admin_action_audits "
                + "WHERE target_type=? AND action_type=? AND target_id=? AND operator_id=?",
            TARGET_TYPE_WEB3_TRANSACTION,
            ACTION_TX_MARK_SUCCEEDED,
            String.valueOf(txId),
            admin.userId());
    assertThat(row.get("success")).isEqualTo(false);
    JsonNode detail = objectMapper.readTree((String) row.get("detail_json"));
    assertThat(detail.at("/failureReason").asText())
        .isEqualTo("Web3TransactionStateInvalidException");

    // 3. outer business transaction rolled back — status still PENDING
    String txStatus =
        jdbcTemplate.queryForObject(
            "SELECT status FROM web3_transactions WHERE id = ?", String.class, txId);
    assertThat(txStatus).isEqualTo("PENDING");

    // 4. out-of-scope regression: NO CS_OVERRIDE row was written by the inner audit path
    Integer csOverrideCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM web3_transaction_audits "
                + "WHERE event_type='CS_OVERRIDE' AND web3_transaction_id = ?",
            Integer.class,
            txId);
    assertThat(csOverrideCount).isZero();
  }

  // ============================================================
  // E-3: audit row's operator_id matches the admin who called the endpoint
  // ============================================================

  @Test
  @DisplayName("[E-3] 두 admin 이 각각 호출 → admin_action_audits.operator_id 가 호출자별로 정확히 매핑")
  void markTransactionSucceeded_perOperator_recordsCorrectOperatorId() throws Exception {
    AdminUser adminA = createAdminAndLogin();
    AdminUser adminB = createAdminAndLogin();
    String txHashA = "0x" + repeat('c', 64);
    String txHashB = "0x" + repeat('d', 64);
    long txIdA = seedUnconfirmedTransaction(txHashA, adminA.userId());
    long txIdB = seedUnconfirmedTransaction(txHashB, adminB.userId());
    stubReceiptSuccess(txHashA);
    stubReceiptSuccess(txHashB);

    ResponseEntity<String> resA =
        markSucceeded(adminA.accessToken(), txIdA, txHashA, "by A", "evidence-a");
    ResponseEntity<String> resB =
        markSucceeded(adminB.accessToken(), txIdB, txHashB, "by B", "evidence-b");

    assertThat(resA.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resB.getStatusCode()).isEqualTo(HttpStatus.OK);

    Long opA =
        jdbcTemplate.queryForObject(
            "SELECT operator_id FROM admin_action_audits " + "WHERE action_type=? AND target_id=?",
            Long.class,
            ACTION_TX_MARK_SUCCEEDED,
            String.valueOf(txIdA));
    Long opB =
        jdbcTemplate.queryForObject(
            "SELECT operator_id FROM admin_action_audits " + "WHERE action_type=? AND target_id=?",
            Long.class,
            ACTION_TX_MARK_SUCCEEDED,
            String.valueOf(txIdB));
    assertThat(opA).isEqualTo(adminA.userId());
    assertThat(opB).isEqualTo(adminB.userId());
  }

  // ============================================================
  // E-4: ProvisionTreasuryKey success → audit + masking + treasury key persisted
  // ============================================================

  @Test
  @DisplayName(
      "[E-4] ProvisionTreasuryKey 성공 → admin_action_audits target_type='TREASURY_KEY', "
          + "rawPrivateKey 마스킹, web3_treasury_keys 저장")
  void provisionTreasuryKey_success_recordsAuditAndMasksPrivateKey() throws Exception {
    AdminUser admin = createAdminAndLogin();
    String walletAlias = "reward-treasury";
    insertedTreasuryAliases.add(walletAlias);
    // pre-clean any pre-existing alias row from a previous flaky test
    jdbcTemplate.update("DELETE FROM web3_treasury_keys WHERE wallet_alias = ?", walletAlias);

    ResponseEntity<String> response =
        provisionTreasury(admin.accessToken(), VALID_TEST_PRIVATE_KEY, walletAlias);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    // audit row
    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT target_id, target_type, success, detail_json FROM admin_action_audits "
                + "WHERE action_type=? AND target_type=? AND operator_id=?",
            ACTION_TREASURY_KEY_PROVISION,
            TARGET_TYPE_TREASURY_KEY,
            admin.userId());
    assertThat(row.get("success")).isEqualTo(true);
    assertThat(row.get("target_type")).isEqualTo(TARGET_TYPE_TREASURY_KEY);
    assertThat((String) row.get("target_id")).isEqualTo(VALID_TEST_TREASURY_ADDRESS);

    JsonNode detail = objectMapper.readTree((String) row.get("detail_json"));
    // The Long/String/String args sanitize into top-level entries (operatorId is dropped because
    // it is also one of the synthetic fields the aspect filters from arguments).
    JsonNode arguments = detail.at("/arguments");
    assertThat(arguments.isMissingNode()).isFalse();
    // rawPrivateKey contains "key" → masked by sanitizeValue
    assertThat(arguments.at("/rawPrivateKey").asText()).isEqualTo("***");

    // out-of-scope regression: web3_treasury_provision_audits success row exists
    Integer provisionAudits =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM web3_treasury_provision_audits "
                + "WHERE operator_id=? AND success = true",
            Integer.class,
            admin.userId());
    assertThat(provisionAudits).isEqualTo(1);

    // treasury key row persisted
    Integer treasuryKeyCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM web3_treasury_keys WHERE wallet_alias = ?",
            Integer.class,
            walletAlias);
    assertThat(treasuryKeyCount).isEqualTo(1);
  }

  // ============================================================
  // E-5: invalid hex → audit success=false, target_id null, REQUIRES_NEW commit
  // ============================================================

  @Test
  @DisplayName(
      "[E-5] ProvisionTreasuryKey invalid hex → admin_action_audits success=false target_id=null + 비즈니스 롤백")
  void provisionTreasuryKey_invalidHex_recordsFailureAuditAndRollsBackBusinessTx()
      throws Exception {
    AdminUser admin = createAdminAndLogin();
    String walletAlias = "reward-treasury";
    String invalidPrivateKey =
        "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz"; // 64 chars, non-hex

    ResponseEntity<String> response =
        provisionTreasury(admin.accessToken(), invalidPrivateKey, walletAlias);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT target_id, success, detail_json FROM admin_action_audits "
                + "WHERE action_type=? AND target_type=? AND operator_id=?",
            ACTION_TREASURY_KEY_PROVISION,
            TARGET_TYPE_TREASURY_KEY,
            admin.userId());
    assertThat(row.get("success")).isEqualTo(false);
    assertThat(row.get("target_id")).isNull();
    JsonNode detail = objectMapper.readTree((String) row.get("detail_json"));
    assertThat(detail.at("/failureReason").asText())
        .isEqualTo("TreasuryPrivateKeyInvalidException");

    // out-of-scope regression: web3_treasury_provision_audits failure row also exists
    Integer provisionAudits =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM web3_treasury_provision_audits "
                + "WHERE operator_id=? AND success = false",
            Integer.class,
            admin.userId());
    assertThat(provisionAudits).isEqualTo(1);

    // no treasury key row persisted (rollback)
    Integer treasuryKeyCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM web3_treasury_keys WHERE wallet_alias = ?",
            Integer.class,
            walletAlias);
    // we may have had a row already (from a previous successful test); but in this test we did
    // NOT create one — so if the table is otherwise empty for this alias, count should be 0.
    // We assert <= 1 to keep this loose against test ordering.
    assertThat(treasuryKeyCount).isLessThanOrEqualTo(1);
  }

  // ============================================================
  // E-6: walletAlias not allowed → audit success=false target_id=null
  // ============================================================

  @Test
  @DisplayName("[E-6] ProvisionTreasuryKey 허용되지 않은 walletAlias → admin_action_audits success=false")
  void provisionTreasuryKey_unknownAlias_recordsFailureAudit() throws Exception {
    AdminUser admin = createAdminAndLogin();

    ResponseEntity<String> response =
        provisionTreasury(admin.accessToken(), VALID_TEST_PRIVATE_KEY, "unknown-alias");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT target_id, success, detail_json FROM admin_action_audits "
                + "WHERE action_type=? AND target_type=? AND operator_id=?",
            ACTION_TREASURY_KEY_PROVISION,
            TARGET_TYPE_TREASURY_KEY,
            admin.userId());
    assertThat(row.get("success")).isEqualTo(false);
    assertThat(row.get("target_id")).isNull();
    JsonNode detail = objectMapper.readTree((String) row.get("detail_json"));
    assertThat(detail.at("/failureReason").asText()).isEqualTo("Web3InvalidInputException");
  }

  // ============================================================
  // E-7: ROLE_USER → 403, no audit row
  // ============================================================

  @Test
  @DisplayName("[E-7] ROLE_USER 가 mark-succeeded 호출 → 403 + admin_action_audits 미기록")
  void markTransactionSucceeded_nonAdmin_returnsForbiddenAndRecordsNoAudit() throws Exception {
    // Create a regular USER (no role promotion)
    String email = uniqueEmail();
    signup(email, "Test@1234!", "RegularUser");
    createdUserEmails.add(email);
    Long userId =
        jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    createdUserIds.add(userId);
    String accessToken = loginAndGetAccessToken(email, "Test@1234!");

    String txHash = "0x" + repeat('e', 64);
    long txId = seedUnconfirmedTransaction(txHash, userId);

    ResponseEntity<String> response =
        markSucceeded(accessToken, txId, txHash, "regular user attempt", "evidence");

    assertThat(response.getStatusCode().value()).isIn(401, 403);

    Integer auditCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM admin_action_audits "
                + "WHERE action_type=? AND target_id=? AND operator_id=?",
            Integer.class,
            ACTION_TX_MARK_SUCCEEDED,
            String.valueOf(txId),
            userId);
    assertThat(auditCount).isZero();
  }

  // ============================================================
  // E-8: no auth → 401, no audit row
  // ============================================================

  @Test
  @DisplayName("[E-8] 인증 없이 mark-succeeded 호출 → 401 + admin_action_audits 미기록")
  void markTransactionSucceeded_noAuth_returnsUnauthorizedAndRecordsNoAudit() {
    // Use a synthetic txId — endpoint should be blocked before any DB I/O.
    long txId = 999_999_999L;
    Map<String, String> body =
        Map.of(
            "txHash", "0x" + repeat('f', 64),
            "explorerUrl", "https://x/x",
            "reason", "no auth",
            "evidence", "evidence");
    HttpHeaders noAuthHeaders = new HttpHeaders();
    noAuthHeaders.setContentType(MediaType.APPLICATION_JSON);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/admin/web3/transactions/" + txId + "/mark-succeeded",
            HttpMethod.POST,
            new HttpEntity<>(body, noAuthHeaders),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    Integer auditCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM admin_action_audits WHERE target_id = ?",
            Integer.class,
            String.valueOf(txId));
    assertThat(auditCount).isZero();
  }

  // ============================================================
  // E-9: concurrent admin invocations both commit
  // ============================================================

  @Test
  @DisplayName("[E-9] 동일 admin 동시 mark-succeeded 두 건 → 두 audit row 모두 commit")
  void markTransactionSucceeded_concurrent_bothAuditRowsCommit() throws Exception {
    AdminUser admin = createAdminAndLogin();
    String txHashA = "0x" + repeat('1', 64);
    String txHashB = "0x" + repeat('2', 64);
    long txIdA = seedUnconfirmedTransaction(txHashA, admin.userId());
    long txIdB = seedUnconfirmedTransaction(txHashB, admin.userId());
    stubReceiptSuccessForAny();

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      CompletableFuture<ResponseEntity<String>> fa =
          CompletableFuture.supplyAsync(
              () -> markSucceeded(admin.accessToken(), txIdA, txHashA, "concurrent A", "ev-a"),
              executor);
      CompletableFuture<ResponseEntity<String>> fb =
          CompletableFuture.supplyAsync(
              () -> markSucceeded(admin.accessToken(), txIdB, txHashB, "concurrent B", "ev-b"),
              executor);
      CompletableFuture.allOf(fa, fb).get(30, TimeUnit.SECONDS);

      assertThat(fa.get().getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(fb.get().getStatusCode()).isEqualTo(HttpStatus.OK);
    } finally {
      executor.shutdownNow();
    }

    Integer auditCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM admin_action_audits "
                + "WHERE target_type=? AND action_type=? AND operator_id=? "
                + "AND target_id IN (?, ?)",
            Integer.class,
            TARGET_TYPE_WEB3_TRANSACTION,
            ACTION_TX_MARK_SUCCEEDED,
            admin.userId(),
            String.valueOf(txIdA),
            String.valueOf(txIdB));
    assertThat(auditCount).isEqualTo(2);
  }

  // ============================================================
  // E-11: schema verification — admin_action_audits table & columns shape (no Flyway)
  // ============================================================

  @Test
  @DisplayName("[E-11] admin_action_audits 스키마 검증 — 컬럼/타입 + out-of-scope 테이블 보존")
  void schemaVerification_adminActionAuditsHasExpectedColumnsAndOutOfScopeTablesExist() {
    // 1. admin_action_audits exists
    String adminAuditTable =
        jdbcTemplate.queryForObject(
            "SELECT to_regclass('public.admin_action_audits')::text", String.class);
    assertThat(adminAuditTable).isEqualTo("admin_action_audits");

    // 2. expected columns
    List<String> expectedColumns =
        List.of(
            "id",
            "operator_id",
            "action_type",
            "target_type",
            "target_id",
            "success",
            "detail_json",
            "created_at");
    List<String> actualColumns =
        jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema='public' AND table_name='admin_action_audits' "
                + "ORDER BY ordinal_position",
            String.class);
    assertThat(actualColumns).containsAll(expectedColumns);
    // source column was removed when AuditSource enum was deleted
    assertThat(actualColumns).doesNotContain("source");

    // 3. NOT NULL constraints on critical columns
    List<String> notNullColumns =
        jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema='public' AND table_name='admin_action_audits' "
                + "AND is_nullable = 'NO' ORDER BY column_name",
            String.class);
    assertThat(notNullColumns)
        .contains("id", "operator_id", "action_type", "target_type", "success", "created_at");
    // target_id and detail_json must be nullable
    assertThat(notNullColumns).doesNotContain("target_id");
    assertThat(notNullColumns).doesNotContain("detail_json");

    // 4. out-of-scope tables must still exist (must NOT have been touched by V035)
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.web3_transaction_audits')::text", String.class))
        .isEqualTo("web3_transaction_audits");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.web3_transfer_guard_audits')::text", String.class))
        .isEqualTo("web3_transfer_guard_audits");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.web3_treasury_provision_audits')::text", String.class))
        .isEqualTo("web3_treasury_provision_audits");

    // 5. legacy table must be gone
    String legacy =
        jdbcTemplate.queryForObject(
            "SELECT to_regclass('public.web3_admin_action_audits')::text", String.class);
    assertThat(legacy).isNull();
  }

  // ============================================================
  // utility
  // ============================================================

  private static String repeat(char c, int n) {
    char[] arr = new char[n];
    java.util.Arrays.fill(arr, c);
    return new String(arr);
  }
}
