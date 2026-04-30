package momzzangseven.mztkbe.integration.e2e.web3.treasury;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import momzzangseven.mztkbe.global.error.treasury.KmsAliasAlreadyExistsException;
import momzzangseven.mztkbe.global.error.web3.SignatureRecoveryException;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyMaterialWrapperPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SignDigestPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import org.web3j.crypto.Credentials;

/**
 * E2E tests for the Treasury Key Lifecycle: Provision → Disable → Archive.
 *
 * <p>KMS ports are replaced with Mockito stubs so the test runs without a real AWS KMS endpoint.
 * The controller requires {@code web3.reward-token.treasury.provisioning.enabled=true} — this is
 * set via {@link TestPropertySource}. {@code web3_treasury_wallets} is excluded from {@link
 * momzzangseven.mztkbe.integration.e2e.support.DatabaseCleaner}; all rows inserted during tests are
 * cleaned manually in {@link #cleanTreasuryWalletRow()}.
 *
 * <p>Test cases: [E2E-1] through [E2E-8].
 */
@TestPropertySource(
    properties = {
      "web3.reward-token.treasury.provisioning.enabled=true",
    })
@DisplayName("[E2E] Treasury Key Lifecycle — Provision / Disable / Archive")
class TreasuryKeyLifecycleE2ETest extends E2ETestBase {

  private static final String PRIVATE_KEY_HEX =
      "4f3edf983ac636a65a842ce7c78d9aa706d3b113bce036f4edc6f6dc0d1e6f73";
  private static final String DERIVED_ADDRESS =
      Credentials.create(PRIVATE_KEY_HEX).getAddress().toLowerCase();
  private static final String REWARD_ALIAS = "reward-treasury";
  private static final String MOCK_KMS_KEY_ID = "e2e-mock-kms-key-id";

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PasswordEncoder passwordEncoder;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private KmsKeyLifecyclePort kmsKeyLifecyclePort;
  @MockitoBean private KmsKeyMaterialWrapperPort kmsKeyMaterialWrapperPort;
  @MockitoBean private SignDigestPort signDigestPort;

  private String adminToken;

  @BeforeEach
  void setUpAdmin() throws Exception {
    String email =
        "treasury-e2e-"
            + UUID.randomUUID().toString().replace("-", "").substring(0, 8)
            + "@internal.mztk.local";
    jdbcTemplate.update(
        "INSERT INTO users (email, role, nickname, created_at, updated_at)"
            + " VALUES (?, 'ADMIN_SEED', 'TreasuryE2EAdmin', NOW(), NOW())",
        email);
    Long userId =
        jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    String loginId =
        "treasury-e2e-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    String plaintext = "TrE2E@Pass" + UUID.randomUUID().toString().substring(0, 8);
    String hash = passwordEncoder.encode(plaintext);
    jdbcTemplate.update(
        "INSERT INTO admin_accounts"
            + " (user_id, login_id, password_hash, created_by,"
            + " last_login_at, password_last_rotated_at, deleted_at, created_at, updated_at)"
            + " VALUES (?, ?, ?, NULL, NULL, NULL, NULL, NOW(), NOW())",
        userId,
        loginId,
        hash);
    ResponseEntity<String> loginRes =
        restTemplate.exchange(
            baseUrl() + "/auth/login",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("provider", "LOCAL_ADMIN", "loginId", loginId, "password", plaintext),
                jsonOnlyHeaders()),
            String.class);
    assertThat(loginRes.getStatusCode()).isEqualTo(HttpStatus.OK);
    adminToken = objectMapper.readTree(loginRes.getBody()).at("/data/accessToken").asText();
    assertThat(adminToken).isNotBlank();
  }

  /** web3_treasury_wallets is excluded from DatabaseCleaner — delete the test row explicitly. */
  @AfterEach
  void cleanTreasuryWalletRow() {
    jdbcTemplate.update("DELETE FROM web3_treasury_wallets WHERE wallet_alias = ?", REWARD_ALIAS);
  }

  // ---------------------------------------------------------------------------
  // DB query helpers (for audit / wallet assertions)
  // ---------------------------------------------------------------------------

  private Integer countWalletRows() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM web3_treasury_wallets WHERE wallet_alias = ?",
        Integer.class,
        REWARD_ALIAS);
  }

  private Integer countProvisionAuditRows(boolean success) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM web3_treasury_provision_audits WHERE success = ?",
        Integer.class,
        success);
  }

  private Integer countKmsAuditRows(String actionType, boolean success) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM web3_treasury_kms_audits WHERE action_type = ? AND success = ?",
        Integer.class,
        actionType,
        success);
  }

  private Map<String, Object> latestProvisionAuditRow() {
    return jdbcTemplate.queryForMap(
        "SELECT operator_id, treasury_address, success, failure_reason FROM"
            + " web3_treasury_provision_audits ORDER BY id DESC LIMIT 1");
  }

  // ---------------------------------------------------------------------------
  // Happy-path provision stubs
  // ---------------------------------------------------------------------------

  private void stubSuccessfulProvision() {
    when(kmsKeyLifecyclePort.createKey()).thenReturn(MOCK_KMS_KEY_ID);
    when(kmsKeyLifecyclePort.getParametersForImport(MOCK_KMS_KEY_ID))
        .thenReturn(new KmsKeyLifecyclePort.ImportParams(new byte[] {1}, new byte[] {2}));
    when(kmsKeyMaterialWrapperPort.wrap(any(byte[].class), any(byte[].class)))
        .thenReturn(new byte[] {3});
    when(signDigestPort.signDigest(anyString(), any(byte[].class), anyString()))
        .thenReturn(new Vrs(new byte[32], new byte[32], (byte) 27));
  }

  // ---------------------------------------------------------------------------
  // HTTP helpers
  // ---------------------------------------------------------------------------

  private ResponseEntity<String> provision() {
    Map<String, Object> body =
        Map.of(
            "rawPrivateKey", PRIVATE_KEY_HEX,
            "role", "REWARD",
            "expectedAddress", DERIVED_ADDRESS);
    return restTemplate.exchange(
        baseUrl() + "/admin/web3/treasury-keys/provision",
        HttpMethod.POST,
        new HttpEntity<>(body, bearerJsonHeaders(adminToken)),
        String.class);
  }

  private ResponseEntity<String> getWallet(String alias) {
    return restTemplate.exchange(
        baseUrl() + "/admin/web3/treasury-keys/" + alias,
        HttpMethod.GET,
        new HttpEntity<>(bearerJsonHeaders(adminToken)),
        String.class);
  }

  private ResponseEntity<String> disableWallet(String alias) {
    return restTemplate.exchange(
        baseUrl() + "/admin/web3/treasury-keys/" + alias + "/disable",
        HttpMethod.POST,
        new HttpEntity<>(bearerJsonHeaders(adminToken)),
        String.class);
  }

  private ResponseEntity<String> archiveWallet(String alias) {
    return restTemplate.exchange(
        baseUrl() + "/admin/web3/treasury-keys/" + alias + "/archive",
        HttpMethod.POST,
        new HttpEntity<>(bearerJsonHeaders(adminToken)),
        String.class);
  }

  // ---------------------------------------------------------------------------
  // [E2E-1] Provision → ACTIVE
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("[E2E-1] POST /provision — valid key+address creates ACTIVE wallet")
  void provision_validInput_returnsActiveWallet() throws Exception {
    stubSuccessfulProvision();

    ResponseEntity<String> res = provision();

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode data = objectMapper.readTree(res.getBody());
    assertThat(data.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(data.at("/data/walletAlias").asText()).isEqualTo(REWARD_ALIAS);
    assertThat(data.at("/data/walletAddress").asText()).isEqualToIgnoringCase(DERIVED_ADDRESS);
    assertThat(data.at("/data/status").asText()).isEqualTo("ACTIVE");
    assertThat(data.at("/data/kmsKeyId").asText()).isEqualTo(MOCK_KMS_KEY_ID);
  }

  // ---------------------------------------------------------------------------
  // [E2E-2] GET after provision returns wallet view
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("[E2E-2] GET /{alias} — returns ACTIVE wallet after provision")
  void get_afterProvision_returnsWalletView() throws Exception {
    stubSuccessfulProvision();
    assertThat(provision().getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<String> res = getWallet(REWARD_ALIAS);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode data = objectMapper.readTree(res.getBody());
    assertThat(data.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(data.at("/data/status").asText()).isEqualTo("ACTIVE");
    assertThat(data.at("/data/walletAlias").asText()).isEqualTo(REWARD_ALIAS);
  }

  // ---------------------------------------------------------------------------
  // [E2E-3] Disable → DISABLED
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[E2E-3 / E-3] POST /{alias}/disable — ACTIVE→DISABLED + AFTER_COMMIT KMS disable + audit row")
  void disable_activeWallet_returnsDisabled() throws Exception {
    stubSuccessfulProvision();
    assertThat(provision().getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<String> res = disableWallet(REWARD_ALIAS);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode data = objectMapper.readTree(res.getBody());
    assertThat(data.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(data.at("/data/status").asText()).isEqualTo("DISABLED");
    assertThat(data.at("/data/walletAddress").asText()).isEqualToIgnoringCase(DERIVED_ADDRESS);

    // AFTER_COMMIT KMS handler
    verify(kmsKeyLifecyclePort).disableKey(MOCK_KMS_KEY_ID);
    assertThat(countKmsAuditRows("KMS_DISABLE", true)).isEqualTo(1);
  }

  // ---------------------------------------------------------------------------
  // [E2E-4] Full lifecycle: provision → disable → archive
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[E2E-4 / E-4 / E-5] 풀 라이프사이클 — provision → disable → archive + AFTER_COMMIT scheduleKeyDeletion(30) + 모든 audit row 검증")
  void fullLifecycle_provision_disable_archive() throws Exception {
    stubSuccessfulProvision();

    // Provision
    ResponseEntity<String> provisionRes = provision();
    assertThat(provisionRes.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(objectMapper.readTree(provisionRes.getBody()).at("/data/status").asText())
        .isEqualTo("ACTIVE");

    // Disable
    ResponseEntity<String> disableRes = disableWallet(REWARD_ALIAS);
    assertThat(disableRes.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(objectMapper.readTree(disableRes.getBody()).at("/data/status").asText())
        .isEqualTo("DISABLED");

    // Archive
    ResponseEntity<String> archiveRes = archiveWallet(REWARD_ALIAS);
    assertThat(archiveRes.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode archiveData = objectMapper.readTree(archiveRes.getBody());
    assertThat(archiveData.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(archiveData.at("/data/status").asText()).isEqualTo("ARCHIVED");

    // [E-4] verify scheduleKeyDeletion called with the 30-day window
    verify(kmsKeyLifecyclePort).scheduleKeyDeletion(MOCK_KMS_KEY_ID, 30);

    // [E-5] every lifecycle step writes audit rows.
    // TreasuryAuditRecorder is shared by Provision/Disable/Archive services and
    // appends one row per call to web3_treasury_provision_audits — provision +
    // disable + archive = 3 success rows.
    assertThat(countProvisionAuditRows(true)).isEqualTo(3);
    assertThat(countKmsAuditRows("KMS_CREATE_ALIAS", true)).isEqualTo(1);
    assertThat(countKmsAuditRows("KMS_DISABLE", true)).isEqualTo(1);
    assertThat(countKmsAuditRows("KMS_SCHEDULE_DELETION", true)).isEqualTo(1);
  }

  // ---------------------------------------------------------------------------
  // [E2E-5] Auth guard — unauthenticated request is rejected
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("인증 오류 케이스")
  class AuthErrors {

    @Test
    @DisplayName("[E2E-5] POST /provision without Authorization header → 401")
    void provision_unauthenticated_returns401() {
      Map<String, Object> body =
          Map.of(
              "rawPrivateKey", PRIVATE_KEY_HEX,
              "role", "REWARD",
              "expectedAddress", DERIVED_ADDRESS);
      ResponseEntity<String> res =
          restTemplate.exchange(
              baseUrl() + "/admin/web3/treasury-keys/provision",
              HttpMethod.POST,
              new HttpEntity<>(body, jsonOnlyHeaders()),
              String.class);
      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("[E2E-6] POST /{alias}/disable without Authorization header → 401")
    void disable_unauthenticated_returns401() {
      ResponseEntity<String> res =
          restTemplate.exchange(
              baseUrl() + "/admin/web3/treasury-keys/" + REWARD_ALIAS + "/disable",
              HttpMethod.POST,
              new HttpEntity<>(jsonOnlyHeaders()),
              String.class);
      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
  }

  // ---------------------------------------------------------------------------
  // [E2E-7] State-transition errors
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("상태 전이 오류 케이스")
  class StateTransitionErrors {

    @Test
    @DisplayName("[E2E-7] POST /disable on non-existent alias → 409 CONFLICT")
    void disable_nonExistentAlias_returns409() {
      ResponseEntity<String> res = disableWallet("non-existent-alias");
      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("[E2E-8] POST /archive on ACTIVE wallet (wrong state) → 409 CONFLICT")
    void archive_activeWallet_returns409() throws Exception {
      stubSuccessfulProvision();
      assertThat(provision().getStatusCode()).isEqualTo(HttpStatus.OK);

      ResponseEntity<String> res = archiveWallet(REWARD_ALIAS);

      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      JsonNode body = objectMapper.readTree(res.getBody());
      assertThat(body.at("/status").asText()).isEqualTo("FAIL");
    }

    @Test
    @DisplayName("[E2E-9 / E-11] POST /disable on already-DISABLED wallet → 409 CONFLICT")
    void disable_alreadyDisabledWallet_returns409() throws Exception {
      stubSuccessfulProvision();
      assertThat(provision().getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(disableWallet(REWARD_ALIAS).getStatusCode()).isEqualTo(HttpStatus.OK);

      ResponseEntity<String> res = disableWallet(REWARD_ALIAS);

      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
  }

  // ---------------------------------------------------------------------------
  // [E-8] Bean validation 400
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("[E-8] 입력 검증 오류 (Bean Validation / 400)")
  class ValidationErrors {

    private ResponseEntity<String> postRaw(Map<String, Object> body) {
      return restTemplate.exchange(
          baseUrl() + "/admin/web3/treasury-keys/provision",
          HttpMethod.POST,
          new HttpEntity<>(body, bearerJsonHeaders(adminToken)),
          String.class);
    }

    @Test
    @DisplayName("[E-8a] rawPrivateKey 누락 → 400")
    void missingPrivateKey_returns400() {
      Map<String, Object> body = new HashMap<>();
      body.put("role", "REWARD");
      body.put("expectedAddress", DERIVED_ADDRESS);

      ResponseEntity<String> res = postRaw(body);

      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("[E-8b] role 누락 → 400")
    void missingRole_returns400() {
      Map<String, Object> body = new HashMap<>();
      body.put("rawPrivateKey", PRIVATE_KEY_HEX);
      body.put("expectedAddress", DERIVED_ADDRESS);

      ResponseEntity<String> res = postRaw(body);

      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("[E-8c] expectedAddress 누락 → 400")
    void missingExpectedAddress_returns400() {
      Map<String, Object> body = new HashMap<>();
      body.put("rawPrivateKey", PRIVATE_KEY_HEX);
      body.put("role", "REWARD");

      ResponseEntity<String> res = postRaw(body);

      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  // ---------------------------------------------------------------------------
  // [E-12]..[E-16], [E-21] Provisioning failure modes
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("Provisioning 실패 분기")
  class ProvisionFailures {

    @Test
    @DisplayName("[E-13] expectedAddress 불일치 → 400 TREASURY_003 + audit FAILURE(ADDRESS_MISMATCH)")
    void mismatchedExpectedAddress_returns400AndAuditsFailure() throws Exception {
      Map<String, Object> body =
          Map.of(
              "rawPrivateKey",
              PRIVATE_KEY_HEX,
              "role",
              "REWARD",
              "expectedAddress",
              "0x" + "f".repeat(40));

      ResponseEntity<String> res =
          restTemplate.exchange(
              baseUrl() + "/admin/web3/treasury-keys/provision",
              HttpMethod.POST,
              new HttpEntity<>(body, bearerJsonHeaders(adminToken)),
              String.class);

      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      JsonNode jsonRoot = objectMapper.readTree(res.getBody());
      assertThat(jsonRoot.at("/status").asText()).isEqualTo("FAIL");
      assertThat(jsonRoot.at("/code").asText()).isEqualTo("TREASURY_003");

      // No wallet row created
      assertThat(countWalletRows()).isZero();

      // Audit row recorded with treasury_address NULL (early-fail branch)
      assertThat(countProvisionAuditRows(false)).isEqualTo(1);
      Map<String, Object> latest = latestProvisionAuditRow();
      assertThat(latest.get("success")).isEqualTo(false);
      assertThat(latest.get("failure_reason")).isEqualTo("ADDRESS_MISMATCH");
      assertThat(latest.get("treasury_address")).isNull();
    }

    @Test
    @DisplayName(
        "[E-14] KMS createKey throws → 500 + cleanup 미호출 + audit FAILURE(RuntimeException)")
    void createKeyThrows_returns500AndCleanupNotCalled() {
      when(kmsKeyLifecyclePort.createKey()).thenThrow(new RuntimeException("KMS unavailable"));

      ResponseEntity<String> res = provision();

      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

      // No wallet, audit FAILURE
      assertThat(countWalletRows()).isZero();
      assertThat(countProvisionAuditRows(false)).isEqualTo(1);

      // Cleanup never invoked because kmsKeyId is null
      verify(kmsKeyLifecyclePort, never()).disableKey(anyString());
      verify(kmsKeyLifecyclePort, never()).scheduleKeyDeletion(anyString(), anyInt());
    }

    @Test
    @DisplayName(
        "[E-15 / E-21] importKeyMaterial throws → 500 + cleanup(disableKey + scheduleKeyDeletion 7) + audit FAILURE 살아남음")
    void importKeyMaterialThrows_returns500AndCleansUpKey() {
      when(kmsKeyLifecyclePort.createKey()).thenReturn(MOCK_KMS_KEY_ID);
      when(kmsKeyLifecyclePort.getParametersForImport(MOCK_KMS_KEY_ID))
          .thenReturn(new KmsKeyLifecyclePort.ImportParams(new byte[] {1}, new byte[] {2}));
      when(kmsKeyMaterialWrapperPort.wrap(any(byte[].class), any(byte[].class)))
          .thenReturn(new byte[] {3});
      doThrowOnImportKeyMaterial(new RuntimeException("import failed"));

      ResponseEntity<String> res = provision();

      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

      // [E-21] outer txn rolled back, no wallet row…
      assertThat(countWalletRows()).isZero();
      // …but audit row (REQUIRES_NEW) survives
      assertThat(countProvisionAuditRows(false)).isEqualTo(1);

      // Cleanup invoked because kmsKeyId was assigned
      verify(kmsKeyLifecyclePort).disableKey(MOCK_KMS_KEY_ID);
      verify(kmsKeyLifecyclePort).scheduleKeyDeletion(MOCK_KMS_KEY_ID, 7);
    }

    @Test
    @DisplayName(
        "[E-16] sanity sign SignatureRecoveryException → 500 + cleanup + audit FAILURE(SignatureRecoveryException)")
    void sanitySignThrows_returns500AndCleansUpKey() {
      when(kmsKeyLifecyclePort.createKey()).thenReturn(MOCK_KMS_KEY_ID);
      when(kmsKeyLifecyclePort.getParametersForImport(MOCK_KMS_KEY_ID))
          .thenReturn(new KmsKeyLifecyclePort.ImportParams(new byte[] {1}, new byte[] {2}));
      when(kmsKeyMaterialWrapperPort.wrap(any(byte[].class), any(byte[].class)))
          .thenReturn(new byte[] {3});
      when(signDigestPort.signDigest(anyString(), any(byte[].class), anyString()))
          .thenThrow(new SignatureRecoveryException("sanity sign mismatch"));

      ResponseEntity<String> res = provision();

      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(countWalletRows()).isZero();
      assertThat(countProvisionAuditRows(false)).isEqualTo(1);

      verify(kmsKeyLifecyclePort).disableKey(MOCK_KMS_KEY_ID);
      verify(kmsKeyLifecyclePort).scheduleKeyDeletion(MOCK_KMS_KEY_ID, 7);
    }

    @Test
    @DisplayName(
        "[E-12] 동일 alias로 provision 재호출 → 409 TREASURY_004 + audit FAILURE(ALREADY_PROVISIONED)")
    void provisionTwice_returns409AlreadyProvisioned() throws Exception {
      stubSuccessfulProvision();
      assertThat(provision().getStatusCode()).isEqualTo(HttpStatus.OK);

      // Force ENABLED to disable alias-repair branch
      when(kmsKeyLifecyclePort.describeAliasTarget(REWARD_ALIAS)).thenReturn(KmsKeyState.ENABLED);

      ResponseEntity<String> res = provision();

      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      JsonNode body = objectMapper.readTree(res.getBody());
      assertThat(body.at("/code").asText()).isEqualTo("TREASURY_004");

      // Only one wallet row
      assertThat(countWalletRows()).isEqualTo(1);
      // Two audit rows: 1 success (first provision) + 1 failure (second)
      assertThat(countProvisionAuditRows(true)).isEqualTo(1);
      assertThat(countProvisionAuditRows(false)).isEqualTo(1);
    }

    /** Helper: configure importKeyMaterial mock to throw. */
    private void doThrowOnImportKeyMaterial(RuntimeException ex) {
      org.mockito.Mockito.doThrow(ex)
          .when(kmsKeyLifecyclePort)
          .importKeyMaterial(anyString(), any(byte[].class), any(byte[].class));
    }
  }

  // ---------------------------------------------------------------------------
  // [E-17] Alias-repair mode
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("[E-17] Alias-repair 모드")
  class AliasRepair {

    @Test
    @DisplayName("[E-17] 기존 row가 있고 alias가 KMS에서 DISABLED → 200, createKey 미호출, kms_key_id 변경 없음")
    void existingRowWithDisabledAlias_repairsViaUpdateAlias() throws Exception {
      // Pre-seed row: 동일 alias + 동일 derived address + 기존 kms_key_id
      String previousKmsId = "prev-kms-id";
      jdbcTemplate.update(
          "INSERT INTO web3_treasury_wallets"
              + " (wallet_alias, kms_key_id, treasury_address, status, key_origin, created_at, updated_at)"
              + " VALUES (?, ?, ?, 'ACTIVE', 'IMPORTED', NOW(), NOW())",
          REWARD_ALIAS,
          previousKmsId,
          DERIVED_ADDRESS);

      // Stub: alias-target은 DISABLED → 알리어스 복구 분기
      when(kmsKeyLifecyclePort.describeAliasTarget(REWARD_ALIAS)).thenReturn(KmsKeyState.DISABLED);
      // BindKmsAlias의 ghost-recovery에서 사용될 createAlias / updateAlias 스텁
      org.mockito.Mockito.doThrow(
              new KmsAliasAlreadyExistsException("alias already bound", new RuntimeException()))
          .when(kmsKeyLifecyclePort)
          .createAlias(eq(REWARD_ALIAS), anyString());

      ResponseEntity<String> res = provision();

      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode data = objectMapper.readTree(res.getBody());
      assertThat(data.at("/data/kmsKeyId").asText()).isEqualTo(previousKmsId);

      // 본 사이클에서는 createKey/import가 실행되면 안 됨
      verify(kmsKeyLifecyclePort, never()).createKey();
      verify(kmsKeyLifecyclePort, never())
          .importKeyMaterial(anyString(), any(byte[].class), any(byte[].class));

      // AFTER_COMMIT 핸들러가 ghost-alias 복구 → updateAlias 사용
      verify(kmsKeyLifecyclePort).updateAlias(REWARD_ALIAS, previousKmsId);
      assertThat(countKmsAuditRows("KMS_UPDATE_ALIAS", true)).isEqualTo(1);
    }
  }

  // ---------------------------------------------------------------------------
  // [E-18] Post-commit BindKmsAlias 실패는 200을 망치지 않음
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("[E-18] AFTER_COMMIT BindKmsAlias 실패")
  class PostCommitFailures {

    @Test
    @DisplayName(
        "[E-18] createAlias가 RuntimeException → 200 응답 유지, kms_audits에 KMS_CREATE_ALIAS 실패 기록")
    void postCommitBindAliasFailure_doesNotRollBackWallet() throws Exception {
      stubSuccessfulProvision();
      // createAlias 만 RuntimeException
      org.mockito.Mockito.doThrow(new RuntimeException("aws sdk error"))
          .when(kmsKeyLifecyclePort)
          .createAlias(eq(REWARD_ALIAS), anyString());

      ResponseEntity<String> res = provision();

      // 트랜잭션은 이미 커밋됨 → 200
      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(countWalletRows()).isEqualTo(1);

      // kms_audits에 실패 기록 (action_type=KMS_CREATE_ALIAS, success=false)
      assertThat(countKmsAuditRows("KMS_CREATE_ALIAS", false)).isEqualTo(1);
    }
  }
}
