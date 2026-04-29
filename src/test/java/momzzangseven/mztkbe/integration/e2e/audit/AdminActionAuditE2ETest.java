package momzzangseven.mztkbe.integration.e2e.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyMaterialWrapperPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SignDigestPort;
import org.junit.jupiter.api.AfterEach;
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
@TestPropertySource(
    properties = {
      "web3.reward-token.enabled=true",
      "web3.reward-token.treasury.provisioning.enabled=true"
    })
@DisplayName("[E2E] admin_action_audits 통합 테이블 write path 테스트")
class AdminActionAuditE2ETest extends E2ETestBase {

  private static final String ACTION_TX_MARK_SUCCEEDED = "TRANSACTION_MARK_SUCCEEDED";
  private static final String ACTION_TREASURY_KEY_PROVISION = "TREASURY_KEY_PROVISION";
  private static final String TARGET_TYPE_WEB3_TRANSACTION = "WEB3_TRANSACTION";
  private static final String TARGET_TYPE_TREASURY_KEY = "TREASURY_KEY";

  /** Hardhat first account private key — test-only, holds no real value. */
  private static final String VALID_TEST_PRIVATE_KEY =
      "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"; // gitleaks:allow

  private static final String VALID_TEST_TREASURY_ADDRESS =
      "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PasswordEncoder passwordEncoder;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private Web3ContractPort web3ContractPort;
  @MockitoBean private KmsKeyLifecyclePort kmsKeyLifecyclePort;
  @MockitoBean private KmsKeyMaterialWrapperPort kmsKeyMaterialWrapperPort;
  @MockitoBean private SignDigestPort signDigestPort;

  private static final String MOCK_KMS_KEY_ID = "audit-e2e-mock-kms-key-id";

  /**
   * {@code web3_treasury_wallets} is excluded from {@code DatabaseCleaner}, so the rows that E-4
   * persists must be torn down explicitly. Idempotent — DELETE on a missing row is a no-op for
   * tests (E-1 ~ E-3, E-5 ~ E-9) that never touch the table.
   */
  @AfterEach
  void cleanTreasuryWalletRows() {
    jdbcTemplate.update(
        "DELETE FROM web3_treasury_wallets WHERE wallet_alias IN ('reward-treasury','sponsor-treasury')");
  }

  /**
   * Stubs the KMS lifecycle / wrapper / signer ports for a successful provision flow. Mirrors
   * {@code TreasuryKeyLifecycleE2ETest#stubSuccessfulProvision} so the two suites stay in sync.
   */
  private void stubSuccessfulProvision() {
    when(kmsKeyLifecyclePort.createKey()).thenReturn(MOCK_KMS_KEY_ID);
    when(kmsKeyLifecyclePort.getParametersForImport(MOCK_KMS_KEY_ID))
        .thenReturn(new KmsKeyLifecyclePort.ImportParams(new byte[] {1}, new byte[] {2}));
    when(kmsKeyMaterialWrapperPort.wrap(any(byte[].class), any(byte[].class)))
        .thenReturn(new byte[] {3});
    when(signDigestPort.signDigest(anyString(), any(byte[].class), anyString()))
        .thenReturn(new Vrs(new byte[32], new byte[32], (byte) 27));
  }

  // ============================================================
  // Helpers
  // ============================================================

  /**
   * Signs up a user, promotes them to ADMIN_GENERATED via direct DB update, creates a matching
   * {@code admin_accounts} row so that the JWT filter's {@code isActiveAdmin} check passes, then
   * logs in.
   */
  private AdminUser createAdminAndLogin() {
    String email = randomEmail();
    Long userId = signupUser(email, DEFAULT_TEST_PASSWORD, "AuditAdmin");
    jdbcTemplate.update("UPDATE users SET role = 'ADMIN_GENERATED' WHERE id = ?", userId);

    String loginId = String.valueOf(10000000 + (int) (Math.random() * 90000000));
    jdbcTemplate.update(
        "INSERT INTO admin_accounts (user_id, login_id, password_hash, created_by,"
            + " last_login_at, password_last_rotated_at, deleted_at, created_at, updated_at)"
            + " VALUES (?, ?, ?, NULL, NULL, NULL, NULL, NOW(), NOW())",
        userId,
        loginId,
        passwordEncoder.encode(DEFAULT_TEST_PASSWORD));

    String accessToken = loginUser(email, DEFAULT_TEST_PASSWORD);
    return new AdminUser(userId, accessToken);
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
            + "from_address, to_address, amount_wei, tx_type, status, tx_hash, signed_at, broadcasted_at, failure_reason, created_at, updated_at"
            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
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
        now,
        "RECEIPT_TIMEOUT",
        now,
        now);
    Long id =
        jdbcTemplate.queryForObject(
            "SELECT id FROM web3_transactions WHERE idempotency_key = ?",
            Long.class,
            idempotencyKey);
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
            + "from_address, to_address, amount_wei, tx_type, status, tx_hash, signed_at, broadcasted_at, created_at, updated_at"
            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
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
        now,
        now,
        now);
    Long id =
        jdbcTemplate.queryForObject(
            "SELECT id FROM web3_transactions WHERE idempotency_key = ?",
            Long.class,
            idempotencyKey);
    return id;
  }

  private void stubReceiptSuccess(String txHash) {
    given(web3ContractPort.getReceipt(txHash))
        .willReturn(new Web3ContractPort.ReceiptResult(txHash, true, true, "main", false, null));
  }

  private void stubReceiptSuccessForAny() {
    given(web3ContractPort.getReceipt(anyString()))
        .willAnswer(
            inv ->
                new Web3ContractPort.ReceiptResult(
                    inv.getArgument(0, String.class), true, true, "main", false, null));
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
        baseUrl() + "/admin/web3/transactions/" + txId + "/mark-succeeded",
        HttpMethod.POST,
        new HttpEntity<>(body, bearerJsonHeaders(accessToken)),
        String.class);
  }

  private ResponseEntity<String> provisionTreasury(
      String accessToken, String rawPrivateKey, String role, String expectedAddress) {
    Map<String, String> body =
        Map.of(
            "rawPrivateKey", rawPrivateKey,
            "role", role,
            "expectedAddress", expectedAddress);
    return restTemplate.exchange(
        baseUrl() + "/admin/web3/treasury-keys/provision",
        HttpMethod.POST,
        new HttpEntity<>(body, bearerJsonHeaders(accessToken)),
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
  // E-4: ProvisionTreasuryKey success → audit + masking + treasury wallet persisted
  // ============================================================

  @Test
  @DisplayName(
      "[E-4] ProvisionTreasuryKey 성공 → admin_action_audits target_type='TREASURY_KEY', "
          + "rawPrivateKey 마스킹, web3_treasury_wallets 저장")
  void provisionTreasuryKey_success_recordsAuditAndMasksPrivateKey() throws Exception {
    AdminUser admin = createAdminAndLogin();
    String walletAlias = "reward-treasury";
    stubSuccessfulProvision();

    ResponseEntity<String> response =
        provisionTreasury(
            admin.accessToken(), VALID_TEST_PRIVATE_KEY, "REWARD", VALID_TEST_TREASURY_ADDRESS);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    // admin_action_audits row written by AdminOnlyAspect around the treasury service
    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT target_id, target_type, success, detail_json FROM admin_action_audits "
                + "WHERE action_type=? AND target_type=? AND operator_id=?",
            ACTION_TREASURY_KEY_PROVISION,
            TARGET_TYPE_TREASURY_KEY,
            admin.userId());
    assertThat(row.get("success")).isEqualTo(true);
    assertThat(row.get("target_type")).isEqualTo(TARGET_TYPE_TREASURY_KEY);
    assertThat((String) row.get("target_id")).isEqualToIgnoringCase(VALID_TEST_TREASURY_ADDRESS);

    // The treasury service signature is execute(ProvisionTreasuryKeyCommand command), so the
    // aspect nests the sanitized record under arguments.command.
    JsonNode detail = objectMapper.readTree((String) row.get("detail_json"));
    JsonNode commandNode = detail.at("/arguments/command");
    assertThat(commandNode.isMissingNode()).isFalse();
    // rawPrivateKey contains "key" → masked by sanitizeValue
    assertThat(commandNode.at("/rawPrivateKey").asText()).isEqualTo("***");
    assertThat(commandNode.at("/role").asText()).isEqualTo("REWARD");
    assertThat(commandNode.at("/expectedAddress").asText())
        .isEqualToIgnoringCase(VALID_TEST_TREASURY_ADDRESS);

    // out-of-scope regression: legacy web3_treasury_provision_audits success row also recorded
    Integer provisionAudits =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM web3_treasury_provision_audits "
                + "WHERE operator_id=? AND success = true",
            Integer.class,
            admin.userId());
    assertThat(provisionAudits).isEqualTo(1);

    // treasury wallet row persisted
    Integer treasuryWalletCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM web3_treasury_wallets WHERE wallet_alias = ?",
            Integer.class,
            walletAlias);
    assertThat(treasuryWalletCount).isEqualTo(1);
  }

  // ============================================================
  // E-5: invalid hex → admin_action_audits success=false target_id=null
  //     ProvisionTreasuryKeyCommand#validate() rejects non-hex inside the service body before any
  //     KMS / persistence call, so AdminOnlyAspect records the failure but the legacy
  //     TreasuryAuditRecorder is never reached and web3_treasury_provision_audits is untouched.
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
        provisionTreasury(
            admin.accessToken(), invalidPrivateKey, "REWARD", VALID_TEST_TREASURY_ADDRESS);

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

    // command.validate() runs before the service's try-catch, so the legacy provision audit
    // recorder never fires for this code path.
    Integer provisionAudits =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM web3_treasury_provision_audits WHERE operator_id=?",
            Integer.class,
            admin.userId());
    assertThat(provisionAudits).isZero();

    // no treasury wallet row persisted (rollback)
    Integer treasuryWalletCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM web3_treasury_wallets WHERE wallet_alias = ?",
            Integer.class,
            walletAlias);
    assertThat(treasuryWalletCount).isZero();
  }

  // ============================================================
  // E-6: address mismatch → admin_action_audits success=false target_id=null
  //     The previous "unknown alias" scenario is obsolete now that `role` is a TreasuryRole enum
  //     and the alias is derived server-side. Address-mismatch is the closest replacement: the
  //     service derives the address from rawPrivateKey, fails the equality check against
  //     command.expectedAddress(), records both audit rows, and throws
  //     TreasuryWalletAddressMismatchException → 400.
  // ============================================================

  @Test
  @DisplayName(
      "[E-6] ProvisionTreasuryKey expectedAddress 불일치 → admin_action_audits success=false + provision audit 실패 row")
  void provisionTreasuryKey_addressMismatch_recordsFailureAudit() throws Exception {
    AdminUser admin = createAdminAndLogin();
    String mismatchedAddress = "0x000000000000000000000000000000000000dead";

    ResponseEntity<String> response =
        provisionTreasury(admin.accessToken(), VALID_TEST_PRIVATE_KEY, "REWARD", mismatchedAddress);

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
        .isEqualTo("TreasuryWalletAddressMismatchException");

    // service writes to TreasuryAuditRecorder before throwing for ADDRESS_MISMATCH, so the legacy
    // provision audit table also has a failure row for this operator.
    Integer provisionAudits =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM web3_treasury_provision_audits "
                + "WHERE operator_id=? AND success = false",
            Integer.class,
            admin.userId());
    assertThat(provisionAudits).isEqualTo(1);
  }

  // ============================================================
  // E-7: ROLE_USER → 403, no audit row
  // ============================================================

  @Test
  @DisplayName("[E-7] ROLE_USER 가 mark-succeeded 호출 → 403 + admin_action_audits 미기록")
  void markTransactionSucceeded_nonAdmin_returnsForbiddenAndRecordsNoAudit() throws Exception {
    // Create a regular USER (no role promotion)
    TestUser user = signupAndLogin("RegularUser");
    Long userId = user.userId();
    String accessToken = user.accessToken();

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
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/admin/web3/transactions/" + txId + "/mark-succeeded",
            HttpMethod.POST,
            new HttpEntity<>(body, jsonOnlyHeaders()),
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
        .contains("id", "action_type", "target_type", "success", "created_at");
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
