package momzzangseven.mztkbe.integration.e2e.web3.treasury;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import momzzangseven.mztkbe.global.error.treasury.KmsAliasAlreadyExistsException;
import momzzangseven.mztkbe.global.error.web3.SignatureRecoveryException;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.integration.e2e.web3.treasury.support.InMemoryKmsKeyLifecycleFake;
import momzzangseven.mztkbe.integration.e2e.web3.treasury.support.TreasuryE2EKmsFakeConfig;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.KmsKeyDescribePort;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.AliasTargetInfo;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.VerifyTreasuryWalletForSignUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.DescribeKmsKeyPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyMaterialWrapperPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SignDigestPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
      // Treasury concurrency tests (E-MOM444-6/7 + MS-2/3) launch 2 concurrent provisions, each
      // opening an outer @Transactional plus several REQUIRES_NEW transactions (ReplaceKmsKey CAS
      // + KmsAuditRecorder + TreasuryAuditRecorder) in its AFTER_COMMIT chain. The default pool
      // size of 4 from application-integration.yml is exhausted under that fan-out and surfaces
      // as silent AFTER_COMMIT handler failures (alias not re-bound, old key not disposed).
      "spring.datasource.hikari.maximum-pool-size=10",
    })
@Import(TreasuryE2EKmsFakeConfig.class)
@DisplayName("[E2E] Treasury Key Lifecycle — Provision / Disable / Archive")
class TreasuryKeyLifecycleE2ETest extends E2ETestBase {

  private static final String PRIVATE_KEY_HEX =
      "4f3edf983ac636a65a842ce7c78d9aa706d3b113bce036f4edc6f6dc0d1e6f73";
  private static final String DERIVED_ADDRESS =
      Credentials.create(PRIVATE_KEY_HEX).getAddress().toLowerCase();
  private static final String REWARD_ALIAS = "reward-treasury";
  private static final String MOCK_KMS_KEY_ID = "e2e-mock-kms-key-id";

  // Second/third raw keys for rotation tests
  private static final String PRIVATE_KEY_HEX_2 =
      "1111111111111111111111111111111111111111111111111111111111111111";
  private static final String DERIVED_ADDRESS_2 =
      Credentials.create(PRIVATE_KEY_HEX_2).getAddress().toLowerCase();
  private static final String PRIVATE_KEY_HEX_3 =
      "2222222222222222222222222222222222222222222222222222222222222222";
  private static final String DERIVED_ADDRESS_3 =
      Credentials.create(PRIVATE_KEY_HEX_3).getAddress().toLowerCase();
  private static final String SPONSOR_ALIAS = "sponsor-treasury";

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PasswordEncoder passwordEncoder;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoSpyBean private KmsKeyLifecyclePort kmsKeyLifecyclePort;
  @Autowired private InMemoryKmsKeyLifecycleFake kmsFake;
  @MockitoBean private KmsKeyMaterialWrapperPort kmsKeyMaterialWrapperPort;
  @MockitoBean private SignDigestPort signDigestPort;
  @MockitoSpyBean private DescribeKmsKeyPort describeKmsKeyPort;
  @MockitoSpyBean private KmsKeyDescribePort sharedKmsKeyDescribePort;
  @Autowired private VerifyTreasuryWalletForSignUseCase verifyTreasuryWalletForSignUseCase;

  private String adminToken;

  @BeforeEach
  void resetKmsFake() {
    kmsFake.reset();
  }

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

  /** web3_treasury_wallets is excluded from DatabaseCleaner — delete the test rows explicitly. */
  @AfterEach
  void cleanTreasuryWalletRow() {
    jdbcTemplate.update(
        "DELETE FROM web3_treasury_wallets WHERE wallet_alias IN (?, ?)",
        REWARD_ALIAS,
        SPONSOR_ALIAS);
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

  /**
   * Counts {@code describe(...)} invocations recorded on the shared describe-port spy. Used by
   * cache-bypass regression guards to assert that {@code describeFresh} reached the shared port.
   */
  private long sharedDescribeInvocationCount() {
    return org.mockito.Mockito.mockingDetails(sharedKmsKeyDescribePort).getInvocations().stream()
        .filter(i -> "describe".equals(i.getMethod().getName()))
        .count();
  }

  // ---------------------------------------------------------------------------
  // Happy-path provision stubs
  // ---------------------------------------------------------------------------

  private void stubSuccessfulProvision() {
    kmsFake.useFixedKeyIdForNextCreate(MOCK_KMS_KEY_ID);
    when(kmsKeyMaterialWrapperPort.wrap(any(byte[].class), any(byte[].class)))
        .thenReturn(new byte[] {3});
    when(signDigestPort.signDigest(anyString(), any(byte[].class), anyString()))
        .thenReturn(new Vrs(new byte[32], new byte[32], (byte) 27));
  }

  /**
   * Configure the KMS fake to hand out a fresh kms-key-id per call. Use this when a test needs
   * multiple provision invocations (rotation, shared-wallet).
   */
  private void stubProvisionWithDynamicKeys() {
    kmsFake.enableDynamicKeyMinting("e2e-kms-key-");
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

  private ResponseEntity<String> provision(
      String role, String rawPrivateKey, String expectedAddress) {
    Map<String, Object> body =
        Map.of("rawPrivateKey", rawPrivateKey, "role", role, "expectedAddress", expectedAddress);
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

  private Map<String, Object> walletRow(String alias) {
    return jdbcTemplate.queryForMap(
        "SELECT wallet_alias, kms_key_id, treasury_address, status, disabled_at"
            + " FROM web3_treasury_wallets WHERE wallet_alias = ?",
        alias);
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
      doThrow(new RuntimeException("KMS unavailable")).when(kmsKeyLifecyclePort).createKey();

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
      kmsFake.useFixedKeyIdForNextCreate(MOCK_KMS_KEY_ID);
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
      kmsFake.useFixedKeyIdForNextCreate(MOCK_KMS_KEY_ID);
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

      // alias points to the row's kmsKeyId in ENABLED state → C4 reject (no alias drift).
      // Fake already tracks this state (createAlias was invoked in AFTER_COMMIT), so no stubbing
      // needed — verify via doReturn for explicitness in case AFTER_COMMIT timing surprises.
      doReturn(new AliasTargetInfo(KmsKeyState.ENABLED, MOCK_KMS_KEY_ID))
          .when(kmsKeyLifecyclePort)
          .describeAlias(REWARD_ALIAS);

      ResponseEntity<String> res = provision();

      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      JsonNode body = objectMapper.readTree(res.getBody());
      assertThat(body.at("/code").asText()).isEqualTo("TREASURY_004");

      // Only one wallet row
      assertThat(countWalletRows()).isEqualTo(1);
      // Two audit rows: 1 success (first provision) + 1 failure (second)
      assertThat(countProvisionAuditRows(true)).isEqualTo(1);
      assertThat(countProvisionAuditRows(false)).isEqualTo(1);
      // C4 idempotent reject path does not mint a new key (PR #177 R4).
      verify(kmsKeyLifecyclePort, times(1)).createKey();
    }

    /** Helper: configure importKeyMaterial mock to throw. */
    private void doThrowOnImportKeyMaterial(RuntimeException ex) {
      doThrow(ex)
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
    @DisplayName(
        "[E-17] 기존 row(ACTIVE) + alias=DISABLED + targetIdMatches → R5 ReactivatedEvent 재발행 → enableKey 호출 (PR #177 R5)")
    void existingRowWithDisabledAlias_repairsViaR5ReactivatedEvent() throws Exception {
      // Pre-seed row: 동일 alias + 동일 derived address + 기존 kms_key_id (ACTIVE 상태).
      String previousKmsId = "prev-kms-id";
      jdbcTemplate.update(
          "INSERT INTO web3_treasury_wallets"
              + " (wallet_alias, kms_key_id, treasury_address, status, key_origin, created_at, updated_at)"
              + " VALUES (?, ?, ?, 'ACTIVE', 'IMPORTED', NOW(), NOW())",
          REWARD_ALIAS,
          previousKmsId,
          DERIVED_ADDRESS);

      // PR #177 R5: DB.status=ACTIVE + alias=DISABLED + targetIdMatches 는 C5 reactivate 가
      // AFTER_COMMIT enableKey 단계에서 실패한 상황으로 본다. handleExistingProvisionedRow 는
      // mint 없이 TreasuryWalletReactivatedEvent 를 재발행하고, 핸들러가 enableKey 를 다시 시도한다.
      doReturn(new AliasTargetInfo(KmsKeyState.DISABLED, previousKmsId))
          .when(kmsKeyLifecyclePort)
          .describeAlias(REWARD_ALIAS);

      ResponseEntity<String> res = provision();

      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode data = objectMapper.readTree(res.getBody());
      // 응답의 kmsKeyId 는 기존 row 의 값을 그대로 반환 (R5 는 row 를 갱신하지 않음).
      assertThat(data.at("/data/kmsKeyId").asText()).isEqualTo(previousKmsId);

      // R5 분기는 mint 자체를 실행하지 않으므로 KMS mint chain 호출이 없다.
      verify(kmsKeyLifecyclePort, never()).createKey();
      verify(kmsKeyLifecyclePort, never())
          .importKeyMaterial(anyString(), any(byte[].class), any(byte[].class));
      // mint 가 없으므로 cleanup (disable + scheduleDeletion) 도 없다.
      verify(kmsKeyLifecyclePort, never()).disableKey(anyString());
      verify(kmsKeyLifecyclePort, never()).scheduleKeyDeletion(anyString(), anyInt());

      // R5: alias 재바인딩 (updateAlias) 대신 enableKey 를 호출한다.
      verify(kmsKeyLifecyclePort, never()).updateAlias(eq(REWARD_ALIAS), anyString());
      verify(kmsKeyLifecyclePort).enableKey(previousKmsId);
      assertThat(countKmsAuditRows("KMS_ENABLE", true)).isEqualTo(1);
    }

    @Test
    @DisplayName(
        "[E-17b] 기존 row가 있고 alias가 ENABLED 이지만 다른 key 를 가리킴 → alias drift → updateAlias 로 복구 (PR #177 R3)")
    void existingRowWithEnabledButDriftedAlias_repairsViaUpdateAlias() throws Exception {
      // Pre-seed row: 동일 alias + 동일 derived address + 기존 kms_key_id
      String previousKmsId = "prev-kms-id";
      String driftedKmsId = "drift-kms-id";
      jdbcTemplate.update(
          "INSERT INTO web3_treasury_wallets"
              + " (wallet_alias, kms_key_id, treasury_address, status, key_origin, created_at, updated_at)"
              + " VALUES (?, ?, ?, 'ACTIVE', 'IMPORTED', NOW(), NOW())",
          REWARD_ALIAS,
          previousKmsId,
          DERIVED_ADDRESS);

      // PR #177 R3: alias 가 ENABLED 이지만 row 의 kmsKeyId 와 다른 key 를 가리키면 alias drift 로
      // 판단해 alias-repair 분기로 라우팅된다.
      doReturn(new AliasTargetInfo(KmsKeyState.ENABLED, driftedKmsId))
          .when(kmsKeyLifecyclePort)
          .describeAlias(REWARD_ALIAS);
      // BindKmsAlias 의 ghost-recovery 에서 사용될 createAlias / updateAlias 스텁
      doThrow(new KmsAliasAlreadyExistsException("alias already bound", new RuntimeException()))
          .when(kmsKeyLifecyclePort)
          .createAlias(eq(REWARD_ALIAS), anyString());

      ResponseEntity<String> res = provision();

      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode data = objectMapper.readTree(res.getBody());
      assertThat(data.at("/data/kmsKeyId").asText()).isEqualTo(previousKmsId);

      // C4 alias-repair: mint 안 함.
      verify(kmsKeyLifecyclePort, never()).createKey();
      // AFTER_COMMIT handler 에서 alias 를 row 의 kmsKeyId 로 재바인딩.
      verify(kmsKeyLifecyclePort).updateAlias(REWARD_ALIAS, previousKmsId);
    }

    @Test
    @DisplayName(
        "[E-17c] DISABLED row + alias=PENDING_DELETION+match → replaceKey(disposeOldKey=false) 로 drift 사전 차단 (PR #177 R8)")
    void disabledRowWithPendingDeletionAlias_routesToReplaceKey() throws Exception {
      // Pre-seed K1 in kmsFake as PENDING_DELETION (alias also points to K1).
      String k1 = "k1-e17c";
      kmsFake.useFixedKeyIdForNextCreate(k1);
      kmsKeyLifecyclePort.createKey();
      kmsKeyLifecyclePort.createAlias(REWARD_ALIAS, k1);
      kmsKeyLifecyclePort.scheduleKeyDeletion(k1, 7);
      assertThat(kmsFake.keyState(k1)).isEqualTo(KmsKeyState.PENDING_DELETION);

      // Pre-seed DB row DISABLED/K1.
      jdbcTemplate.update(
          "INSERT INTO web3_treasury_wallets"
              + " (wallet_alias, kms_key_id, treasury_address, status, key_origin,"
              + " disabled_at, created_at, updated_at)"
              + " VALUES (?, ?, ?, 'DISABLED', 'IMPORTED', NOW(), NOW(), NOW())",
          REWARD_ALIAS,
          k1,
          DERIVED_ADDRESS);

      // Mint output for the next createKey (the R8 replaceKey branch).
      String k2 = "k2-e17c";
      kmsFake.useFixedKeyIdForNextCreate(k2);
      when(kmsKeyMaterialWrapperPort.wrap(any(byte[].class), any(byte[].class)))
          .thenReturn(new byte[] {3});
      when(signDigestPort.signDigest(anyString(), any(byte[].class), anyString()))
          .thenReturn(new Vrs(new byte[32], new byte[32], (byte) 27));

      // Isolate post-clear invocations from the fake setup above.
      clearInvocations(kmsKeyLifecyclePort);

      ResponseEntity<String> res = provision();

      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode data = objectMapper.readTree(res.getBody());
      assertThat(data.at("/data/kmsKeyId").asText()).isEqualTo(k2);

      Map<String, Object> row = walletRow(REWARD_ALIAS);
      assertThat((String) row.get("kms_key_id")).isEqualTo(k2);
      assertThat((String) row.get("status")).isEqualTo("ACTIVE");

      // R8 replaceKey branch: mint runs, but disposeOldKey=false so the dying K1 is untouched.
      verify(kmsKeyLifecyclePort, times(1)).createKey();
      verify(kmsKeyLifecyclePort, never()).disableKey(k1);
      verify(kmsKeyLifecyclePort, never()).scheduleKeyDeletion(eq(k1), anyInt());
      verify(kmsKeyLifecyclePort).updateAlias(REWARD_ALIAS, k2);

      // K1 state in the KMS fake must remain PENDING_DELETION — no drift.
      assertThat(kmsFake.aliasTarget(REWARD_ALIAS)).isEqualTo(k2);
      assertThat(kmsFake.keyState(k1)).isEqualTo(KmsKeyState.PENDING_DELETION);
      assertThat(kmsFake.keyState(k2)).isEqualTo(KmsKeyState.ENABLED);

      // targetIdMatches=true → the row-key describe RPC must not fire.
      verify(describeKmsKeyPort, never()).describe(anyString());
    }

    @Test
    @DisplayName(
        "[E-17d] DISABLED row + alias drift + K1 살아있음 → reEnable + alias-repair 동시 처리 (PR #177 R8)")
    void disabledRowWithAliasDriftAndAliveRowKey_publishesBothEvents() throws Exception {
      // Pre-seed fake: K_other ENABLED + alias→K_other; K1 DISABLED (row's key).
      String kOther = "k-other-e17d";
      String k1 = "k1-e17d";
      kmsFake.useFixedKeyIdForNextCreate(kOther);
      kmsKeyLifecyclePort.createKey();
      kmsKeyLifecyclePort.createAlias(REWARD_ALIAS, kOther);
      kmsFake.useFixedKeyIdForNextCreate(k1);
      kmsKeyLifecyclePort.createKey();
      kmsKeyLifecyclePort.disableKey(k1);
      assertThat(kmsFake.keyState(k1)).isEqualTo(KmsKeyState.DISABLED);
      assertThat(kmsFake.aliasTarget(REWARD_ALIAS)).isEqualTo(kOther);

      // Pre-seed DB row DISABLED/K1.
      jdbcTemplate.update(
          "INSERT INTO web3_treasury_wallets"
              + " (wallet_alias, kms_key_id, treasury_address, status, key_origin,"
              + " disabled_at, created_at, updated_at)"
              + " VALUES (?, ?, ?, 'DISABLED', 'IMPORTED', NOW(), NOW(), NOW())",
          REWARD_ALIAS,
          k1,
          DERIVED_ADDRESS);

      // R8 takes the alias-mismatch-and-alive branch: describeKmsKey(K1) classifies K1 as alive.
      doReturn(KmsKeyState.DISABLED).when(describeKmsKeyPort).describe(k1);

      clearInvocations(kmsKeyLifecyclePort);

      ResponseEntity<String> res = provision();

      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode data = objectMapper.readTree(res.getBody());
      assertThat(data.at("/data/kmsKeyId").asText()).isEqualTo(k1);

      Map<String, Object> row = walletRow(REWARD_ALIAS);
      assertThat((String) row.get("kms_key_id")).isEqualTo(k1);
      assertThat((String) row.get("status")).isEqualTo("ACTIVE");

      // R8 alias-mismatch+alive: no mint.
      verify(kmsKeyLifecyclePort, never()).createKey();
      // Reactivated handler → enableKey(K1) brings K1 to ENABLED.
      verify(kmsKeyLifecyclePort).enableKey(k1);
      // Provisioned(aliasRepair=true) handler → createAlias throws AlreadyExists → updateAlias.
      verify(kmsKeyLifecyclePort).updateAlias(REWARD_ALIAS, k1);

      assertThat(kmsFake.aliasTarget(REWARD_ALIAS)).isEqualTo(k1);
      assertThat(kmsFake.keyState(k1)).isEqualTo(KmsKeyState.ENABLED);

      assertThat(countKmsAuditRows("KMS_ENABLE", true)).isGreaterThanOrEqualTo(1);
      assertThat(countKmsAuditRows("KMS_UPDATE_ALIAS", true)).isGreaterThanOrEqualTo(1);
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
      doThrow(new RuntimeException("aws sdk error"))
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

  // ---------------------------------------------------------------------------
  // [MOM-444] action scenarios
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("[MOM-444] action scenarios")
  class Mom444ActionScenarios {

    @Test
    @DisplayName(
        "[E-MOM444-1] 공유 운영 지갑 — REWARD provision 후 동일 raw key 로 SPONSOR provision (C0+C0)")
    void sharedWalletAcrossRoles() throws Exception {
      stubProvisionWithDynamicKeys();

      // REWARD provision
      ResponseEntity<String> rewardRes = provision("REWARD", PRIVATE_KEY_HEX, DERIVED_ADDRESS);
      assertThat(rewardRes.getStatusCode()).isEqualTo(HttpStatus.OK);

      // SPONSOR provision with SAME raw key — previously 409, now 200
      ResponseEntity<String> sponsorRes = provision("SPONSOR", PRIVATE_KEY_HEX, DERIVED_ADDRESS);
      assertThat(sponsorRes.getStatusCode()).isEqualTo(HttpStatus.OK);

      Map<String, Object> rewardRow = walletRow(REWARD_ALIAS);
      Map<String, Object> sponsorRow = walletRow(SPONSOR_ALIAS);

      assertThat((String) rewardRow.get("treasury_address")).isEqualToIgnoringCase(DERIVED_ADDRESS);
      assertThat((String) sponsorRow.get("treasury_address"))
          .isEqualToIgnoringCase(DERIVED_ADDRESS);
      assertThat(rewardRow.get("kms_key_id")).isNotEqualTo(sponsorRow.get("kms_key_id"));
      assertThat((String) rewardRow.get("status")).isEqualTo("ACTIVE");
      assertThat((String) sponsorRow.get("status")).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName(
        "[E-MOM444-2] Key rotation — REWARD provision 후 다른 raw key 로 REWARD 재 provision (C7)")
    void rotation_disposesOldKey() throws Exception {
      stubProvisionWithDynamicKeys();

      ResponseEntity<String> firstRes = provision("REWARD", PRIVATE_KEY_HEX, DERIVED_ADDRESS);
      assertThat(firstRes.getStatusCode()).isEqualTo(HttpStatus.OK);
      String firstKmsKeyId = (String) walletRow(REWARD_ALIAS).get("kms_key_id");

      ResponseEntity<String> rotatedRes = provision("REWARD", PRIVATE_KEY_HEX_2, DERIVED_ADDRESS_2);
      assertThat(rotatedRes.getStatusCode()).isEqualTo(HttpStatus.OK);

      Map<String, Object> row = walletRow(REWARD_ALIAS);
      assertThat((String) row.get("kms_key_id")).isNotEqualTo(firstKmsKeyId);
      assertThat((String) row.get("treasury_address")).isEqualToIgnoringCase(DERIVED_ADDRESS_2);

      // Old key DISABLE + SCHEDULE_DELETION should have been called via AFTER_COMMIT handler
      verify(kmsKeyLifecyclePort).disableKey(firstKmsKeyId);
      verify(kmsKeyLifecyclePort).scheduleKeyDeletion(eq(firstKmsKeyId), eq(7));
    }

    @Test
    @DisplayName("[E-MOM444-3] DISABLED 에서 reactivate (C5) — KMS_ENABLE audit + status ACTIVE")
    void disabledReactivate_enablesKmsAndPromotesToActive() throws Exception {
      stubProvisionWithDynamicKeys();

      assertThat(provision("REWARD", PRIVATE_KEY_HEX, DERIVED_ADDRESS).getStatusCode())
          .isEqualTo(HttpStatus.OK);
      String firstKmsKeyId = (String) walletRow(REWARD_ALIAS).get("kms_key_id");

      assertThat(disableWallet(REWARD_ALIAS).getStatusCode()).isEqualTo(HttpStatus.OK);

      // Re-provision same key + same address → C5
      ResponseEntity<String> reactivated = provision("REWARD", PRIVATE_KEY_HEX, DERIVED_ADDRESS);
      assertThat(reactivated.getStatusCode()).isEqualTo(HttpStatus.OK);

      Map<String, Object> row = walletRow(REWARD_ALIAS);
      assertThat((String) row.get("kms_key_id")).isEqualTo(firstKmsKeyId);
      assertThat((String) row.get("status")).isEqualTo("ACTIVE");
      assertThat(row.get("disabled_at")).isNull();

      verify(kmsKeyLifecyclePort).enableKey(firstKmsKeyId);
      assertThat(countKmsAuditRows("KMS_ENABLE", true)).isGreaterThanOrEqualTo(1);
      // PR #177 R4: C5 reactivate path does not mint a new KMS key — only the first provision
      // (FreshProvision) called createKey.
      verify(kmsKeyLifecyclePort, times(1)).createKey();
    }

    @Test
    @DisplayName("[E-MOM444-4] ARCHIVED 에서 재 provision (C6) — 새 key, 기존 key 는 손대지 않음")
    void archivedReprovision_skipsOldKeyDispose() throws Exception {
      stubProvisionWithDynamicKeys();

      assertThat(provision("REWARD", PRIVATE_KEY_HEX, DERIVED_ADDRESS).getStatusCode())
          .isEqualTo(HttpStatus.OK);
      String firstKmsKeyId = (String) walletRow(REWARD_ALIAS).get("kms_key_id");

      assertThat(disableWallet(REWARD_ALIAS).getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(archiveWallet(REWARD_ALIAS).getStatusCode()).isEqualTo(HttpStatus.OK);

      // Same address re-provision after ARCHIVE → C6 ReplaceKey, disposeOldKey=false
      int kmsDisableBefore = countKmsAuditRows("KMS_DISABLE", true);
      int kmsScheduleBefore = countKmsAuditRows("KMS_SCHEDULE_DELETION", true);

      ResponseEntity<String> reprovisioned = provision("REWARD", PRIVATE_KEY_HEX, DERIVED_ADDRESS);
      assertThat(reprovisioned.getStatusCode()).isEqualTo(HttpStatus.OK);

      Map<String, Object> row = walletRow(REWARD_ALIAS);
      String newKmsKeyId = (String) row.get("kms_key_id");
      assertThat(newKmsKeyId).isNotEqualTo(firstKmsKeyId);
      assertThat((String) row.get("status")).isEqualTo("ACTIVE");

      // No additional KMS_DISABLE / KMS_SCHEDULE_DELETION on old key from this re-provision
      assertThat(countKmsAuditRows("KMS_DISABLE", true)).isEqualTo(kmsDisableBefore);
      assertThat(countKmsAuditRows("KMS_SCHEDULE_DELETION", true)).isEqualTo(kmsScheduleBefore);

      // PR #177 R6-B — alias / key-state assertions via the in-memory KMS fake.
      // C6 = ReplaceKey(disposeOldKey=false): alias points to the new key, the new key is ENABLED.
      assertThat(kmsFake.aliasTarget(REWARD_ALIAS)).isEqualTo(newKmsKeyId);
      assertThat(kmsFake.keyState(newKmsKeyId)).isEqualTo(KmsKeyState.ENABLED);
      // The old key was already PENDING_DELETION (archive ran scheduleKeyDeletion); C6 must leave
      // it untouched — still PENDING_DELETION, never re-disabled or re-scheduled.
      assertThat(kmsFake.keyState(firstKmsKeyId)).isEqualTo(KmsKeyState.PENDING_DELETION);
    }

    @Test
    @DisplayName("[E-MOM444-5] address mismatch + 다른 key 로 ROTATION (C7) 자동 라우팅")
    void diffAddressActive_routedToRotation() throws Exception {
      stubProvisionWithDynamicKeys();

      assertThat(provision("REWARD", PRIVATE_KEY_HEX, DERIVED_ADDRESS).getStatusCode())
          .isEqualTo(HttpStatus.OK);

      // ACTIVE 상태에서 다른 key 로 provision → previously AddressMismatch, now C7 rotation
      ResponseEntity<String> rotated = provision("REWARD", PRIVATE_KEY_HEX_2, DERIVED_ADDRESS_2);
      assertThat(rotated.getStatusCode()).isEqualTo(HttpStatus.OK);

      Map<String, Object> row = walletRow(REWARD_ALIAS);
      assertThat((String) row.get("treasury_address")).isEqualToIgnoringCase(DERIVED_ADDRESS_2);
    }

    @Test
    @DisplayName("[E-MOM444-6] 동일 alias 두 동시 provision — row lock 으로 직렬화")
    void concurrentProvisionSameAlias_serializesViaRowLock() throws Exception {
      stubProvisionWithDynamicKeys();

      // Seed initial REWARD row so the concurrent calls follow the ReplaceKey path
      assertThat(provision("REWARD", PRIVATE_KEY_HEX, DERIVED_ADDRESS).getStatusCode())
          .isEqualTo(HttpStatus.OK);

      java.util.concurrent.ExecutorService pool =
          java.util.concurrent.Executors.newFixedThreadPool(2);
      try {
        java.util.concurrent.Future<ResponseEntity<String>> t1 =
            pool.submit(() -> provision("REWARD", PRIVATE_KEY_HEX_2, DERIVED_ADDRESS_2));
        java.util.concurrent.Future<ResponseEntity<String>> t2 =
            pool.submit(() -> provision("REWARD", PRIVATE_KEY_HEX_3, DERIVED_ADDRESS_3));

        ResponseEntity<String> r1 = t1.get(15, java.util.concurrent.TimeUnit.SECONDS);
        ResponseEntity<String> r2 = t2.get(15, java.util.concurrent.TimeUnit.SECONDS);

        // Both should succeed (serialized via row lock); final row reflects one of the two
        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> finalRow = walletRow(REWARD_ALIAS);
        String finalAddr = (String) finalRow.get("treasury_address");
        assertThat(finalAddr)
            .isIn(DERIVED_ADDRESS_2.toLowerCase(), DERIVED_ADDRESS_3.toLowerCase());

        // PR #177 R6-B — alias must point to the final DB key id even after two concurrent
        // rotations. R1 in-port CAS gate is what prevents the earlier-rotation's stale
        // AFTER_COMMIT handler from re-binding alias to a disposed key.
        String finalKmsKeyId = (String) finalRow.get("kms_key_id");
        assertThat(kmsFake.aliasTarget(REWARD_ALIAS))
            .as("alias must converge to the final DB kms_key_id — no stale revert")
            .isEqualTo(finalKmsKeyId);
        assertThat(kmsFake.keyState(finalKmsKeyId)).isEqualTo(KmsKeyState.ENABLED);
      } finally {
        pool.shutdown();
      }
    }

    @Test
    @DisplayName(
        "[E-MOM444-7] 신규 alias 두 동시 provision — loser 는 race-retry 시 409 TREASURY_004"
            + " (PR #177 R6-C), race 가 안 일어나면 alias-repair 200")
    void concurrentFreshProvision_resolvedByUniqueConstraint() throws Exception {
      stubProvisionWithDynamicKeys();

      java.util.concurrent.ExecutorService pool =
          java.util.concurrent.Executors.newFixedThreadPool(2);
      try {
        java.util.concurrent.Future<ResponseEntity<String>> t1 =
            pool.submit(() -> provision("REWARD", PRIVATE_KEY_HEX, DERIVED_ADDRESS));
        java.util.concurrent.Future<ResponseEntity<String>> t2 =
            pool.submit(() -> provision("REWARD", PRIVATE_KEY_HEX, DERIVED_ADDRESS));

        ResponseEntity<String> r1 = t1.get(15, java.util.concurrent.TimeUnit.SECONDS);
        ResponseEntity<String> r2 = t2.get(15, java.util.concurrent.TimeUnit.SECONDS);

        // Outcome depends on JVM thread scheduling: if both reach loadByAliasForUpdate before
        // either INSERT commits → real DIV race → R6-C retry → loser 409 TREASURY_004.
        // If T2's lockedCommit runs entirely after T1's commit → C4 idempotent path → either
        // 409 ALREADY_PROVISIONED (alias already bound by T1 AFTER_COMMIT) or 200 alias-repair
        // (T1 AFTER_COMMIT bind hadn't fired yet). All three are valid; the invariants below
        // are what matters.
        assertThat(r1.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CONFLICT);
        assertThat(r2.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CONFLICT);
        assertThat(java.util.List.of(r1.getStatusCode(), r2.getStatusCode()))
            .as("at least one winner")
            .contains(HttpStatus.OK);

        // Single row, ACTIVE — race-loser never created a second row.
        assertThat(countWalletRows()).isEqualTo(1);
        Map<String, Object> row = walletRow(REWARD_ALIAS);
        assertThat((String) row.get("status")).isEqualTo("ACTIVE");

        // Alias bound to the surviving DB row's key.
        String winnerKeyId = (String) row.get("kms_key_id");
        assertThat(kmsFake.aliasTarget(REWARD_ALIAS)).isEqualTo(winnerKeyId);
        assertThat(kmsFake.keyState(winnerKeyId)).isEqualTo(KmsKeyState.ENABLED);

        // Loser-specific assertions, only when R6-C race-retry fired (HTTP 409 + TREASURY_004).
        ResponseEntity<String> loser =
            r1.getStatusCode() == HttpStatus.CONFLICT
                ? r1
                : (r2.getStatusCode() == HttpStatus.CONFLICT ? r2 : null);
        boolean raceRetryFired = false;
        if (loser != null) {
          JsonNode loserBody = objectMapper.readTree(loser.getBody());
          if ("TREASURY_004".equals(loserBody.at("/code").asText())) {
            // Could be either R6-C race-retry OR C4 ENABLED+match — only count race-retry by
            // the audit reason below.
            Long raceAuditCount =
                jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM web3_treasury_provision_audits"
                        + " WHERE success = false AND failure_reason = 'FRESH_PROVISION_RACE'",
                    Long.class);
            raceRetryFired = raceAuditCount != null && raceAuditCount >= 1;
          }
        }

        // If race-retry fired, the loser's mint produced an orphan key that the rollback sync
        // disposed (disable + scheduleKeyDeletion → PENDING_DELETION). Non-race paths leave no
        // orphan because no second mint happened.
        for (String otherKey : kmsFake.allKeyIds()) {
          if (!otherKey.equals(winnerKeyId)) {
            assertThat(kmsFake.keyState(otherKey))
                .as("orphan key '%s' must be disposed (race-retry path)", otherKey)
                .isIn(KmsKeyState.DISABLED, KmsKeyState.PENDING_DELETION);
            assertThat(raceRetryFired)
                .as("orphan key exists → race-retry must have fired")
                .isTrue();
          }
        }
      } finally {
        pool.shutdown();
      }
    }
  }

  // ---------------------------------------------------------------------------
  // [MOM-444 Step 2] In-port CAS gate scenarios
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("[MOM-444 Step 2] in-port CAS gate scenarios")
  class Mom444Step2CasGateScenarios {

    @Autowired
    private momzzangseven.mztkbe.modules.web3.treasury.application.port.in.ReplaceKmsKeyUseCase
        replaceKmsKeyUseCase;

    @Autowired
    private momzzangseven.mztkbe.modules.web3.treasury.application.port.in.DisableKmsKeyUseCase
        disableKmsKeyUseCase;

    @Test
    @DisplayName(
        "[E-MOM444-8] R5 — enableKey 실패 후 재 provision 으로 회복 (DB ACTIVE / KMS DISABLED →"
            + " handleExistingProvisionedRow → ReactivatedEvent 재발행 → CAS pass → enableKey 재호출)")
    void r5_enableKeyFailureRecovery_replaysReactivatedAndEnablesKey() throws Exception {
      stubProvisionWithDynamicKeys();

      // 1) C0 FreshProvision — mint K0, DB row ACTIVE.
      assertThat(provision("REWARD", PRIVATE_KEY_HEX, DERIVED_ADDRESS).getStatusCode())
          .isEqualTo(HttpStatus.OK);
      String firstKmsKeyId = (String) walletRow(REWARD_ALIAS).get("kms_key_id");

      // 2) Disable the wallet → DB DISABLED, AFTER_COMMIT disableKey(firstKmsKeyId).
      assertThat(disableWallet(REWARD_ALIAS).getStatusCode()).isEqualTo(HttpStatus.OK);

      // 3) Stub enableKey to throw on the FIRST call, succeed on subsequent calls.
      doThrow(new RuntimeException("simulated enableKey failure"))
          .doNothing()
          .when(kmsKeyLifecyclePort)
          .enableKey(firstKmsKeyId);

      // 4) Second provision (C5 ReEnableSameKey) — handler's enableKey throws but is swallowed.
      //    CAS in EnableKmsKeyService passes (DB now ACTIVE + kmsKeyId matches) → enableKey is
      //    actually invoked → throws → KMS_ENABLE success=false audit. HTTP stays 200.
      ResponseEntity<String> reEnableRes = provision("REWARD", PRIVATE_KEY_HEX, DERIVED_ADDRESS);
      assertThat(reEnableRes.getStatusCode()).isEqualTo(HttpStatus.OK);
      Map<String, Object> afterReEnableRow = walletRow(REWARD_ALIAS);
      assertThat((String) afterReEnableRow.get("status")).isEqualTo("ACTIVE");

      // 5) Third provision (R5 trigger) — DB.status=ACTIVE so handleExistingProvisionedRow runs.
      //    Stub describeAlias to report DISABLED with matching kmsKeyId → R5 branch publishes
      //    TreasuryWalletReactivatedEvent → handler invokes EnableKmsKeyUseCase → CAS pass →
      //    enableKey now succeeds (per the chained stub above).
      doReturn(new AliasTargetInfo(KmsKeyState.DISABLED, firstKmsKeyId))
          .when(kmsKeyLifecyclePort)
          .describeAlias(REWARD_ALIAS);
      ResponseEntity<String> r5Res = provision("REWARD", PRIVATE_KEY_HEX, DERIVED_ADDRESS);
      assertThat(r5Res.getStatusCode()).isEqualTo(HttpStatus.OK);

      // Final DB state — same key, ACTIVE, disabled_at null.
      Map<String, Object> finalRow = walletRow(REWARD_ALIAS);
      assertThat((String) finalRow.get("kms_key_id")).isEqualTo(firstKmsKeyId);
      assertThat((String) finalRow.get("status")).isEqualTo("ACTIVE");
      assertThat(finalRow.get("disabled_at")).isNull();

      // enableKey was invoked twice — first failed, second succeeded.
      verify(kmsKeyLifecyclePort, times(2)).enableKey(firstKmsKeyId);

      // Audit rows: ≥1 failure + ≥1 success on KMS_ENABLE; no KMS_ENABLE_SKIPPED (CAS passed both).
      assertThat(countKmsAuditRows("KMS_ENABLE", false)).isGreaterThanOrEqualTo(1);
      assertThat(countKmsAuditRows("KMS_ENABLE", true)).isGreaterThanOrEqualTo(1);
      assertThat(countKmsAuditRows("KMS_ENABLE_SKIPPED", true)).isEqualTo(0);

      // Only the initial FreshProvision minted — R5 path never mints.
      verify(kmsKeyLifecyclePort, times(1)).createKey();
    }

    @Test
    @DisplayName("[E-MOM444-9] Stale Replace event — chain orphan 방지 with CAS skip")
    void staleReplaceEvent_recordsSkipAudit_andDoesNotRevertAlias() throws Exception {
      stubProvisionWithDynamicKeys();

      // Seed K0
      assertThat(provision("REWARD", PRIVATE_KEY_HEX, DERIVED_ADDRESS).getStatusCode())
          .isEqualTo(HttpStatus.OK);
      String k0 = (String) walletRow(REWARD_ALIAS).get("kms_key_id");

      // Rotate K0 → K1
      assertThat(provision("REWARD", PRIVATE_KEY_HEX_2, DERIVED_ADDRESS_2).getStatusCode())
          .isEqualTo(HttpStatus.OK);
      String k1 = (String) walletRow(REWARD_ALIAS).get("kms_key_id");

      // Rotate K1 → K2
      assertThat(provision("REWARD", PRIVATE_KEY_HEX_3, DERIVED_ADDRESS_3).getStatusCode())
          .isEqualTo(HttpStatus.OK);
      String k2 = (String) walletRow(REWARD_ALIAS).get("kms_key_id");

      // Sanity — three distinct keys, row sits at K2.
      assertThat(k0).isNotEqualTo(k1);
      assertThat(k1).isNotEqualTo(k2);
      assertThat((String) walletRow(REWARD_ALIAS).get("kms_key_id")).isEqualTo(k2);

      // Pre-stale audit snapshots
      int skipBefore = countKmsAuditRows("KMS_REPLACE_SKIPPED", true);
      int updateAliasBefore = countKmsAuditRows("KMS_UPDATE_ALIAS", true);

      // Clear Mockito invocation history so verify(...) below reflects ONLY the stale execute.
      // (The K0→K1 rotation naturally called updateAlias("reward-treasury", k1) and disabled K0;
      // we want to assert behaviors of the stale handler specifically.)
      clearInvocations(kmsKeyLifecyclePort);

      // Manually invoke the in-port with a stale K0→K1 command (as if the K0→K1 handler
      // fired late, AFTER the K1→K2 rotation already advanced the row).
      replaceKmsKeyUseCase.execute(
          new momzzangseven.mztkbe.modules.web3.treasury.application.dto.ReplaceKmsKeyCommand(
              REWARD_ALIAS, k0, k1, DERIVED_ADDRESS_2, 1L, true));

      // DB.kmsKeyId still K2 — alias drift prevention worked.
      assertThat((String) walletRow(REWARD_ALIAS).get("kms_key_id")).isEqualTo(k2);

      // Exactly one new KMS_REPLACE_SKIPPED audit row (reason=KEY_ID_MISMATCH internally).
      assertThat(countKmsAuditRows("KMS_REPLACE_SKIPPED", true)).isEqualTo(skipBefore + 1);

      // No additional KMS_UPDATE_ALIAS row — stale handler did NOT touch the alias.
      assertThat(countKmsAuditRows("KMS_UPDATE_ALIAS", true)).isEqualTo(updateAliasBefore);

      // Post-clear: alias was never re-bound to K1 by the stale handler.
      verify(kmsKeyLifecyclePort, never()).updateAlias(REWARD_ALIAS, k1);

      // Orphan dispose path fires for K0 (command.oldKmsKeyId=K0 != current.kmsKeyId=K2).
      // After clearInvocations the stale handler's disableKey/scheduleKeyDeletion on K0 are
      // the only invocations on the mock — assert each happened exactly once via the orphan path.
      verify(kmsKeyLifecyclePort, times(1)).disableKey(k0);
      verify(kmsKeyLifecyclePort, times(1)).scheduleKeyDeletion(eq(k0), eq(7));
    }

    @Test
    @DisplayName(
        "[E-MOM444-10] R7 — reverse-order Disable/Reactivate race 의 stale Disable 이벤트가"
            + " DB.status=ACTIVE 인 row 에 도달하면 CAS skip + KMS_DISABLE_SKIPPED audit, KMS"
            + " disableKey 미호출 (DB ACTIVE / KMS ENABLED drift 방지)")
    void staleDisableEvent_skipsViaCasGate_andDoesNotRevertKmsState() throws Exception {
      stubProvisionWithDynamicKeys();

      // 1) C0 FreshProvision — mint K0, DB row ACTIVE, KMS ENABLED.
      assertThat(provision("REWARD", PRIVATE_KEY_HEX, DERIVED_ADDRESS).getStatusCode())
          .isEqualTo(HttpStatus.OK);
      String k0 = (String) walletRow(REWARD_ALIAS).get("kms_key_id");

      // 2) Disable wallet — DB DISABLED, AFTER_COMMIT disableKey(K0) → KMS DISABLED.
      assertThat(disableWallet(REWARD_ALIAS).getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(kmsFake.keyState(k0)).isEqualTo(KmsKeyState.DISABLED);

      // 3) Re-provision same key (C5 ReEnableSameKey) — DB ACTIVE, AFTER_COMMIT enableKey(K0)
      //    → KMS ENABLED. This puts the slot in the exact state the reverse-order race produces:
      //    DB ACTIVE / KMS ENABLED, ready to be corrupted by a delayed Disable handler.
      assertThat(provision("REWARD", PRIVATE_KEY_HEX, DERIVED_ADDRESS).getStatusCode())
          .isEqualTo(HttpStatus.OK);
      Map<String, Object> rowBefore = walletRow(REWARD_ALIAS);
      assertThat((String) rowBefore.get("status")).isEqualTo("ACTIVE");
      assertThat((String) rowBefore.get("kms_key_id")).isEqualTo(k0);
      assertThat(kmsFake.keyState(k0)).isEqualTo(KmsKeyState.ENABLED);

      int skipBefore = countKmsAuditRows("KMS_DISABLE_SKIPPED", true);
      int disableBefore = countKmsAuditRows("KMS_DISABLE", true);

      // Isolate post-clear invocations so verify(disableKey, never()) reflects ONLY the stale call.
      clearInvocations(kmsKeyLifecyclePort);

      // 4) Manually invoke the in-port with a stale Disable(K0) command — as if the original
      //    step-2 Disable AFTER_COMMIT handler fired late, AFTER C5 already flipped the row back
      //    to ACTIVE. R7 CAS gate must detect STATUS_MISMATCH and skip.
      disableKmsKeyUseCase.execute(
          new momzzangseven.mztkbe.modules.web3.treasury.application.dto.DisableKmsKeyCommand(
              REWARD_ALIAS, k0, DERIVED_ADDRESS, 1L));

      // DB / KMS state must be unchanged — no drift.
      Map<String, Object> rowAfter = walletRow(REWARD_ALIAS);
      assertThat((String) rowAfter.get("status")).isEqualTo("ACTIVE");
      assertThat((String) rowAfter.get("kms_key_id")).isEqualTo(k0);
      assertThat(kmsFake.keyState(k0)).isEqualTo(KmsKeyState.ENABLED);

      // KMS disableKey was NOT called by the stale handler.
      verify(kmsKeyLifecyclePort, never()).disableKey(anyString());

      // Exactly one new KMS_DISABLE_SKIPPED audit row; no new KMS_DISABLE row.
      assertThat(countKmsAuditRows("KMS_DISABLE_SKIPPED", true)).isEqualTo(skipBefore + 1);
      assertThat(countKmsAuditRows("KMS_DISABLE", true)).isEqualTo(disableBefore);
      assertThat(countKmsAuditRows("KMS_DISABLE_SKIPPED", false)).isZero();
    }

    @Test
    @DisplayName(
        "[E-MOM444-11] R8 — DISABLED row + row key가 외부 개입으로 PENDING_DELETION → re-provision 이"
            + " replaceKey(disposeOldKey=false) 로 fresh key 교체, 죽어가는 K1 은 손대지 않음 (drift 사전 차단)")
    void r8_disabledRowWithExternallyPendingDeletionKey_routesToReplaceKey() throws Exception {
      stubProvisionWithDynamicKeys();

      // 1) C0 FreshProvision — mint K0, DB ACTIVE, KMS ENABLED.
      assertThat(provision("REWARD", PRIVATE_KEY_HEX, DERIVED_ADDRESS).getStatusCode())
          .isEqualTo(HttpStatus.OK);
      String k0 = (String) walletRow(REWARD_ALIAS).get("kms_key_id");

      // 2) Disable wallet — DB DISABLED, AFTER_COMMIT disableKey(K0) → KMS DISABLED.
      assertThat(disableWallet(REWARD_ALIAS).getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(kmsFake.keyState(k0)).isEqualTo(KmsKeyState.DISABLED);

      // 3) Simulate external operator intervention: K0 driven to PENDING_DELETION outside the app.
      //    Bypass the spy by mutating the fake directly so the call isn't recorded against the
      //    Mockito invocation history.
      kmsFake.scheduleKeyDeletion(k0, 7);
      assertThat(kmsFake.keyState(k0)).isEqualTo(KmsKeyState.PENDING_DELETION);
      // alias still points to K0 — disable does not touch the alias.
      assertThat(kmsFake.aliasTarget(REWARD_ALIAS)).isEqualTo(k0);

      int updateAliasBefore = countKmsAuditRows("KMS_UPDATE_ALIAS", true);
      int disableBefore = countKmsAuditRows("KMS_DISABLE", true);
      int scheduleBefore = countKmsAuditRows("KMS_SCHEDULE_DELETION", true);
      clearInvocations(kmsKeyLifecyclePort);

      // 4) Re-provision with the same key+address. Pre-R8 this would have committed DB ACTIVE/K0
      //    then thrown KMSInvalidStateException on the AFTER_COMMIT enableKey. R8 detects K0 is
      //    dying via aliasInfo.state() (targetIdMatches=true) and routes to replaceKey.
      ResponseEntity<String> reRes = provision("REWARD", PRIVATE_KEY_HEX, DERIVED_ADDRESS);
      assertThat(reRes.getStatusCode()).isEqualTo(HttpStatus.OK);

      Map<String, Object> finalRow = walletRow(REWARD_ALIAS);
      String k1 = (String) finalRow.get("kms_key_id");
      assertThat(k1).isNotEqualTo(k0);
      assertThat((String) finalRow.get("status")).isEqualTo("ACTIVE");

      // R8 replaceKey branch fired: createKey ran, alias retargeted to K1.
      verify(kmsKeyLifecyclePort, times(1)).createKey();
      assertThat(kmsFake.aliasTarget(REWARD_ALIAS)).isEqualTo(k1);
      assertThat(kmsFake.keyState(k1)).isEqualTo(KmsKeyState.ENABLED);
      assertThat(countKmsAuditRows("KMS_UPDATE_ALIAS", true)).isEqualTo(updateAliasBefore + 1);

      // disposeOldKey=false — the dying K0 must not be re-disabled or re-scheduled.
      verify(kmsKeyLifecyclePort, never()).disableKey(k0);
      verify(kmsKeyLifecyclePort, never()).scheduleKeyDeletion(eq(k0), anyInt());
      assertThat(countKmsAuditRows("KMS_DISABLE", true)).isEqualTo(disableBefore);
      assertThat(countKmsAuditRows("KMS_SCHEDULE_DELETION", true)).isEqualTo(scheduleBefore);
      assertThat(kmsFake.keyState(k0)).isEqualTo(KmsKeyState.PENDING_DELETION);

      // targetIdMatches=true so the row-key describe RPC isn't issued.
      verify(describeKmsKeyPort, never()).describe(anyString());

      // enableKey on K0 was never called — drift impossible.
      verify(kmsKeyLifecyclePort, never()).enableKey(k0);
    }

    @Test
    @DisplayName(
        "[E-MOM444-stale-cache-recovery] DISABLED row + 외부 schedule-deletion + 외부 alias 재바인딩 →"
            + " provisioning-recovery 가 describeFresh 로 stale cache 우회 → replaceKey 라우팅 (Commit 1 회귀 가드)")
    void provisionAfterExternalScheduleDeletion_freshDescribeRoutesToReplaceKey() throws Exception {
      stubProvisionWithDynamicKeys();

      // 1) C0 FreshProvision — mint K1, DB ACTIVE, KMS ENABLED, alias→K1.
      assertThat(provision("REWARD", PRIVATE_KEY_HEX, DERIVED_ADDRESS).getStatusCode())
          .isEqualTo(HttpStatus.OK);
      String k1 = (String) walletRow(REWARD_ALIAS).get("kms_key_id");

      // 2) Cache warm-up via the signing-path entry point — populates DescribeKmsKeyService's
      //    Caffeine cache with K1→ENABLED. Goes through the real shared describe port (spy)
      //    while no stub is in place, so the cache stores the live value.
      verifyTreasuryWalletForSignUseCase.execute(REWARD_ALIAS);
      verify(sharedKmsKeyDescribePort, times(1)).describe(k1);

      // 3) Disable the wallet — DB DISABLED, AFTER_COMMIT disableKey(K1) → KMS DISABLED.
      assertThat(disableWallet(REWARD_ALIAS).getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(kmsFake.keyState(k1)).isEqualTo(KmsKeyState.DISABLED);

      // 4) External operator intervention #1 — K1 is driven to PENDING_DELETION outside the app
      //    (bypass the spy so the call isn't recorded against Mockito invocation history).
      kmsFake.scheduleKeyDeletion(k1, 7);
      assertThat(kmsFake.keyState(k1)).isEqualTo(KmsKeyState.PENDING_DELETION);

      // 5) External operator intervention #2 — alias is re-bound to a new key K2 outside the app.
      //    Pre-mint K2 in the fake so it's a known key id; then stub describeAlias on the spy to
      //    report the externally-rotated alias target. (The fake's aliasToKeyId map still records
      //    K1, but the production code reads alias state via describeAlias which is now
      //    stubbed — this matches the realistic scenario where AWS state diverged from the local
      //    fake's view.)
      String k2 = "k2-stale-cache-recovery";
      kmsFake.useFixedKeyIdForNextCreate(k2);
      kmsKeyLifecyclePort.createKey(); // mint K2 in fake → ENABLED
      doReturn(new AliasTargetInfo(KmsKeyState.ENABLED, k2))
          .when(kmsKeyLifecyclePort)
          .describeAlias(REWARD_ALIAS);

      // 6) Stub the SHARED describe port to return PENDING_DELETION on subsequent calls. The
      //    cache already holds K1→ENABLED from step 2, so cached execute(K1) still returns
      //    ENABLED (cache hit, the stub is bypassed). Only executeFresh(K1) — used by
      //    handleExistingDisabledRow's mismatch branch — reaches the spy and gets the stubbed
      //    PENDING_DELETION. This is exactly what Commit 1's describeFresh channel guards.
      doReturn(KmsKeyState.PENDING_DELETION).when(sharedKmsKeyDescribePort).describe(k1);

      int updateAliasBefore = countKmsAuditRows("KMS_UPDATE_ALIAS", true);
      int disableBefore = countKmsAuditRows("KMS_DISABLE", true);
      int scheduleBefore = countKmsAuditRows("KMS_SCHEDULE_DELETION", true);
      long sharedDescribeCallsBefore = sharedDescribeInvocationCount();
      clearInvocations(kmsKeyLifecyclePort);

      // 7) Re-provision with the same raw key + address. Path: addressMatches=true →
      //    DISABLED row → handleExistingDisabledRow. describeAlias returns (ENABLED, K2),
      //    targetIdMatches=false (K1 != K2) → describeFresh(K1) returns PENDING_DELETION via
      //    the spy stub (bypassing the still-ENABLED cache entry). rowKeyDying=true →
      //    replaceKey(disposeOldKey=false). AFTER_COMMIT replaces alias → K3, K1 untouched.
      ResponseEntity<String> reRes = provision("REWARD", PRIVATE_KEY_HEX, DERIVED_ADDRESS);
      assertThat(reRes.getStatusCode()).isEqualTo(HttpStatus.OK);

      Map<String, Object> finalRow = walletRow(REWARD_ALIAS);
      String k3 = (String) finalRow.get("kms_key_id");
      assertThat(k3).isNotEqualTo(k1);
      assertThat(k3).isNotEqualTo(k2);
      assertThat((String) finalRow.get("status")).isEqualTo("ACTIVE");

      // Regression-guard #1 — replaceKey branch fired: createKey ran, alias retargeted to K3.
      verify(kmsKeyLifecyclePort, times(1)).createKey();
      assertThat(kmsFake.aliasTarget(REWARD_ALIAS)).isEqualTo(k3);
      assertThat(kmsFake.keyState(k3)).isEqualTo(KmsKeyState.ENABLED);
      assertThat(countKmsAuditRows("KMS_UPDATE_ALIAS", true)).isEqualTo(updateAliasBefore + 1);

      // Regression-guard #2 — disposeOldKey=false: the dying K1 must not be re-disabled or
      // re-scheduled. K1 stays PENDING_DELETION.
      verify(kmsKeyLifecyclePort, never()).disableKey(k1);
      verify(kmsKeyLifecyclePort, never()).scheduleKeyDeletion(eq(k1), anyInt());
      assertThat(countKmsAuditRows("KMS_DISABLE", true)).isEqualTo(disableBefore);
      assertThat(countKmsAuditRows("KMS_SCHEDULE_DELETION", true)).isEqualTo(scheduleBefore);
      assertThat(kmsFake.keyState(k1)).isEqualTo(KmsKeyState.PENDING_DELETION);

      // Regression-guard #3 — the shared describe port was invoked at least once AFTER the stub
      // was installed (i.e. describeFresh actually reached the spy). Pre-Commit-1, the production
      // code would have called the cached treasury describe path which short-circuits on the
      // cache hit and never reaches the shared port — leaving the call count unchanged. The
      // bug-fix wires describeFresh through this exact spy.
      long sharedDescribeCallsAfter = sharedDescribeInvocationCount();
      assertThat(sharedDescribeCallsAfter)
          .as("describeFresh must reach the shared port even when the cache holds a stale value")
          .isGreaterThan(sharedDescribeCallsBefore);
    }
  }

  // ---------------------------------------------------------------------------
  // [MOM-444 Step 3] Concurrency matrix — multi-step pipeline + concurrent ops
  // ---------------------------------------------------------------------------

  /**
   * Multi-step pipeline + concurrent-op scenarios that exercise the full DB → KMS chain end-to-end
   * and verify the {@link #assertTreasuryInvariantsHold()} invariants (alias ↔ DB kms_key_id,
   * status ↔ KMS state, non-current keys disposed). Built on the {@link
   * InMemoryKmsKeyLifecycleFake} so KMS-side state is observable.
   *
   * <p>The latch-based stale-handler interleaving (originally planned as MS-4) is intentionally
   * omitted here — synchronous {@code @TransactionalEventListener(AFTER_COMMIT)} + the {@code
   * REQUIRES_NEW + loadByAliasForUpdate} row-lock-per-handler structure prevents the production
   * race the gate would simulate. The R1 CAS gate is already covered directly by {@code
   * [E-MOM444-9]} via a manual {@code ReplaceKmsKeyUseCase} invocation.
   */
  @Nested
  @DisplayName("[MOM-444 Step 3] Concurrency matrix — pipeline + concurrent ops")
  class Mom444Step3ConcurrencyMatrix {

    @Test
    @DisplayName("[E-MOM444-MS-1] provision → replace → replace → disable — 최종 상태 invariant")
    void sequentialPipeline_finalState() throws Exception {
      kmsFake.enableDynamicKeyMinting("e2e-kms-key-");

      assertThat(provision("REWARD", PRIVATE_KEY_HEX, DERIVED_ADDRESS).getStatusCode())
          .isEqualTo(HttpStatus.OK);
      String k1 = (String) walletRow(REWARD_ALIAS).get("kms_key_id");

      assertThat(provision("REWARD", PRIVATE_KEY_HEX_2, DERIVED_ADDRESS_2).getStatusCode())
          .isEqualTo(HttpStatus.OK);
      String k2 = (String) walletRow(REWARD_ALIAS).get("kms_key_id");

      assertThat(provision("REWARD", PRIVATE_KEY_HEX_3, DERIVED_ADDRESS_3).getStatusCode())
          .isEqualTo(HttpStatus.OK);
      String k3 = (String) walletRow(REWARD_ALIAS).get("kms_key_id");

      assertThat(disableWallet(REWARD_ALIAS).getStatusCode()).isEqualTo(HttpStatus.OK);

      Map<String, Object> row = walletRow(REWARD_ALIAS);
      assertThat((String) row.get("status")).isEqualTo("DISABLED");
      assertThat((String) row.get("kms_key_id")).isEqualTo(k3);

      // Final invariants
      assertTreasuryInvariantsHold();

      // Disposed key trail — both replaces dispose their predecessor; disable() only disables K3.
      assertThat(kmsFake.keyState(k1)).isEqualTo(KmsKeyState.PENDING_DELETION);
      assertThat(kmsFake.keyState(k2)).isEqualTo(KmsKeyState.PENDING_DELETION);
      assertThat(kmsFake.keyState(k3)).isEqualTo(KmsKeyState.DISABLED);
    }

    @Test
    @DisplayName("[E-MOM444-MS-2] 동시 rotation × 2 — alias 가 final DB key 로 수렴 (R1 CAS 효과)")
    void concurrentRotation_twoReplaces_aliasConvergesToFinalDbKey() throws Exception {
      kmsFake.enableDynamicKeyMinting("e2e-kms-key-");

      // 초기 상태: K1 / ACTIVE / addr_1
      assertThat(provision("REWARD", PRIVATE_KEY_HEX, DERIVED_ADDRESS).getStatusCode())
          .isEqualTo(HttpStatus.OK);

      java.util.concurrent.ExecutorService pool =
          java.util.concurrent.Executors.newFixedThreadPool(2);
      try {
        java.util.concurrent.Future<ResponseEntity<String>> f1 =
            pool.submit(() -> provision("REWARD", PRIVATE_KEY_HEX_2, DERIVED_ADDRESS_2));
        java.util.concurrent.Future<ResponseEntity<String>> f2 =
            pool.submit(() -> provision("REWARD", PRIVATE_KEY_HEX_3, DERIVED_ADDRESS_3));
        ResponseEntity<String> r1 = f1.get(15, java.util.concurrent.TimeUnit.SECONDS);
        ResponseEntity<String> r2 = f2.get(15, java.util.concurrent.TimeUnit.SECONDS);

        // 둘 다 lock-serialized 한 ACTIVE+address-mismatch → replaceKey 경로 → 200.
        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.OK);

        // R1 CAS 핵심 검증 — alias 가 final DB key 와 일치 (stale handler 가 alias 를 되돌리지 않음).
        assertTreasuryInvariantsHold();

        // ACTIVE 라면 두 rotation 중 마지막 lock holder 의 address 와 일치해야.
        Map<String, Object> row = walletRow(REWARD_ALIAS);
        assertThat((String) row.get("treasury_address"))
            .isIn(DERIVED_ADDRESS_2.toLowerCase(), DERIVED_ADDRESS_3.toLowerCase());
        assertThat((String) row.get("status")).isEqualTo("ACTIVE");
      } finally {
        pool.shutdown();
      }
    }

    @Test
    @DisplayName("[E-MOM444-MS-3] 동시 replace + disable — 최종 alias/key state 일관 (직렬화 순서 무관)")
    void concurrentReplaceAndDisable_finalStateConsistent() throws Exception {
      kmsFake.enableDynamicKeyMinting("e2e-kms-key-");

      assertThat(provision("REWARD", PRIVATE_KEY_HEX, DERIVED_ADDRESS).getStatusCode())
          .isEqualTo(HttpStatus.OK); // K1 / ACTIVE

      java.util.concurrent.ExecutorService pool =
          java.util.concurrent.Executors.newFixedThreadPool(2);
      try {
        java.util.concurrent.Future<ResponseEntity<String>> fReplace =
            pool.submit(() -> provision("REWARD", PRIVATE_KEY_HEX_2, DERIVED_ADDRESS_2));
        java.util.concurrent.Future<ResponseEntity<String>> fDisable =
            pool.submit(() -> disableWallet(REWARD_ALIAS));
        ResponseEntity<String> rReplace = fReplace.get(15, java.util.concurrent.TimeUnit.SECONDS);
        ResponseEntity<String> rDisable = fDisable.get(15, java.util.concurrent.TimeUnit.SECONDS);

        // Replace 는 항상 200 (어떤 상태에서 시작하든 replaceKey 경로 OK). Disable 은 ACTIVE 에서
        // 시작 시 200, replaceKey 가 먼저 끝나 DISABLED 가 된 후 다시 disable 호출시 409 가능.
        assertThat(rReplace.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rDisable.getStatusCode())
            .as("disable lock-order dependent: OK first, or 409 if replace landed first")
            .isIn(HttpStatus.OK, HttpStatus.CONFLICT);

        // 최종 invariant: alias=DB key, status↔KMS state, 비-current key disposed
        assertTreasuryInvariantsHold();

        // 가능한 종착 status 는 ACTIVE (replace 가 disable 이후 진입) 또는 DISABLED (disable 이 후행).
        String finalStatus = (String) walletRow(REWARD_ALIAS).get("status");
        assertThat(finalStatus).isIn("ACTIVE", "DISABLED");
      } finally {
        pool.shutdown();
      }
    }
  }

  /**
   * Verify the three core treasury invariants after any sequence of operations:
   *
   * <ol>
   *   <li>alias points to the DB row's {@code kms_key_id}
   *   <li>the KMS key's state matches the DB row's status (ACTIVE↔ENABLED, DISABLED↔DISABLED,
   *       ARCHIVED↔PENDING_DELETION)
   *   <li>every other key the fake knows about is disposed (DISABLED or PENDING_DELETION)
   * </ol>
   */
  private void assertTreasuryInvariantsHold() {
    Map<String, Object> row = walletRow(REWARD_ALIAS);
    String dbKeyId = (String) row.get("kms_key_id");
    String dbStatus = (String) row.get("status");

    java.util.List<Map<String, Object>> kmsAudits =
        jdbcTemplate.queryForList(
            "SELECT action_type, success, failure_reason, kms_key_id"
                + " FROM web3_treasury_kms_audits ORDER BY id");
    java.util.List<Map<String, Object>> provAudits =
        jdbcTemplate.queryForList(
            "SELECT success, failure_reason, treasury_address"
                + " FROM web3_treasury_provision_audits ORDER BY id");
    String snapshot =
        String.format(
            "%n[Treasury invariant snapshot]%n  db.kms_key_id=%s db.status=%s alias→%s%n"
                + "  keyStates=%s%n  kmsAudits=%s%n  provAudits=%s",
            dbKeyId,
            dbStatus,
            kmsFake.aliasTarget(REWARD_ALIAS),
            kmsFake.allKeyIds().stream().map(k -> k + "=" + kmsFake.keyState(k)).sorted().toList(),
            kmsAudits,
            provAudits);

    assertThat(kmsFake.aliasTarget(REWARD_ALIAS))
        .as("Invariant I1 — alias must point to DB kms_key_id (no stale revert)" + snapshot)
        .isEqualTo(dbKeyId);

    KmsKeyState expectedState =
        switch (dbStatus) {
          case "ACTIVE" -> KmsKeyState.ENABLED;
          case "DISABLED" -> KmsKeyState.DISABLED;
          case "ARCHIVED" -> KmsKeyState.PENDING_DELETION;
          default -> throw new AssertionError("Unsupported DB status: " + dbStatus);
        };
    assertThat(kmsFake.keyState(dbKeyId))
        .as("Invariant I2 — KMS key state must match DB status (%s)%s", dbStatus, snapshot)
        .isEqualTo(expectedState);

    for (String otherKey : kmsFake.allKeyIds()) {
      if (!otherKey.equals(dbKeyId)) {
        assertThat(kmsFake.keyState(otherKey))
            .as("Invariant I3 — non-current key '%s' must be disposed%s", otherKey, snapshot)
            .isIn(KmsKeyState.DISABLED, KmsKeyState.PENDING_DELETION);
      }
    }
  }
}
