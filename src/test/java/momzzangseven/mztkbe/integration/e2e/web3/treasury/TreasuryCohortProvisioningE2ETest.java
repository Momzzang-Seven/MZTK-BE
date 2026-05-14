package momzzangseven.mztkbe.integration.e2e.web3.treasury;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyMaterialWrapperPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SignDigestPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.web3j.crypto.Credentials;

/**
 * E2E tests for MOM-444 treasury cohort provisioning. A cohort is the set of {@code
 * web3_treasury_wallets} rows sharing one {@code (treasury_address, kms_key_id)} pair; provisioning
 * a second alias whose private key derives to the same address <em>co-binds</em> to the cohort's
 * shared key instead of minting a new one, and disable / archive transition the whole cohort as a
 * unit while firing the KMS key mutation exactly once.
 *
 * <p>KMS ports are replaced with Mockito stubs so the test runs without a real AWS KMS endpoint.
 * {@code web3_treasury_wallets} is excluded from {@link
 * momzzangseven.mztkbe.integration.e2e.support.DatabaseCleaner}; all rows are cleaned manually in
 * {@link #cleanTreasuryWalletRows()}.
 *
 * <p>Test cases: [E-20], [E-21], [E-30], [E-31], [E-32], [E-33].
 */
@TestPropertySource(
    properties = {
      "web3.reward-token.treasury.provisioning.enabled=true",
    })
@DisplayName("[E2E] Treasury Cohort Provisioning — co-bind / cohort disable / cohort archive")
class TreasuryCohortProvisioningE2ETest extends E2ETestBase {

  private static final String PRIVATE_KEY_HEX =
      "4f3edf983ac636a65a842ce7c78d9aa706d3b113bce036f4edc6f6dc0d1e6f73";
  private static final String DERIVED_ADDRESS =
      Credentials.create(PRIVATE_KEY_HEX).getAddress().toLowerCase();
  private static final String REWARD_ALIAS = "reward-treasury";
  private static final String SPONSOR_ALIAS = "sponsor-treasury";
  private static final String MOCK_KMS_KEY_ID = "e2e-cohort-mock-kms-key-id";

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
        "treasury-cohort-e2e-"
            + UUID.randomUUID().toString().replace("-", "").substring(0, 8)
            + "@internal.mztk.local";
    jdbcTemplate.update(
        "INSERT INTO users (email, role, nickname, created_at, updated_at)"
            + " VALUES (?, 'ADMIN_SEED', 'TreasuryCohortE2EAdmin', NOW(), NOW())",
        email);
    Long userId =
        jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    String loginId =
        "treasury-cohort-e2e-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    String plaintext = "TrCoE2E@Pass" + UUID.randomUUID().toString().substring(0, 8);
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

  /** web3_treasury_wallets is excluded from DatabaseCleaner — delete the cohort rows explicitly. */
  @AfterEach
  void cleanTreasuryWalletRows() {
    jdbcTemplate.update(
        "DELETE FROM web3_treasury_wallets WHERE wallet_alias IN (?, ?)",
        REWARD_ALIAS,
        SPONSOR_ALIAS);
  }

  // ---------------------------------------------------------------------------
  // Mock / DB helpers
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

  private void seedWalletRow(String alias, String address, String kmsKeyId, String status) {
    jdbcTemplate.update(
        "INSERT INTO web3_treasury_wallets"
            + " (wallet_alias, treasury_address, kms_key_id, status, key_origin,"
            + " created_at, updated_at)"
            + " VALUES (?, ?, ?, ?, 'IMPORTED', NOW(), NOW())",
        alias,
        address,
        kmsKeyId,
        status);
  }

  private Integer countWalletRows(String alias) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM web3_treasury_wallets WHERE wallet_alias = ?", Integer.class, alias);
  }

  private String walletStatus(String alias) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM web3_treasury_wallets WHERE wallet_alias = ?", String.class, alias);
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
        "SELECT operator_id, wallet_alias, treasury_address, success, failure_reason FROM"
            + " web3_treasury_provision_audits ORDER BY id DESC LIMIT 1");
  }

  // ---------------------------------------------------------------------------
  // HTTP helpers
  // ---------------------------------------------------------------------------

  private ResponseEntity<String> provision(String role) {
    Map<String, Object> body =
        Map.of("rawPrivateKey", PRIVATE_KEY_HEX, "role", role, "expectedAddress", DERIVED_ADDRESS);
    return restTemplate.exchange(
        baseUrl() + "/admin/web3/treasury-keys/provision",
        HttpMethod.POST,
        new HttpEntity<>(body, bearerJsonHeaders(adminToken)),
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
  // [E-20] Co-bind reuses the cohort's shared key
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("[E-20] Co-bind — 동일 주소의 두 번째 alias 는 cohort 의 공유 키를 재사용 (createKey 1회)")
  void coBind_secondAliasReusesSharedKey() throws Exception {
    stubSuccessfulProvision();

    // first alias: fresh provision creates the KMS key
    assertThat(provision("REWARD").getStatusCode()).isEqualTo(HttpStatus.OK);

    // second alias deriving to the same address: co-bind, no new key
    ResponseEntity<String> res = provision("SPONSOR");

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode data = objectMapper.readTree(res.getBody());
    assertThat(data.at("/data/walletAlias").asText()).isEqualTo(SPONSOR_ALIAS);
    assertThat(data.at("/data/kmsKeyId").asText()).isEqualTo(MOCK_KMS_KEY_ID);

    // both rows persist and share one (address, key) pair
    assertThat(countWalletRows(REWARD_ALIAS)).isEqualTo(1);
    assertThat(countWalletRows(SPONSOR_ALIAS)).isEqualTo(1);
    List<String> distinctKeys =
        jdbcTemplate.queryForList(
            "SELECT DISTINCT kms_key_id FROM web3_treasury_wallets WHERE treasury_address = ?",
            String.class,
            DERIVED_ADDRESS);
    assertThat(distinctKeys).containsExactly(MOCK_KMS_KEY_ID);

    // createKey invoked exactly once (the fresh provision only)
    verify(kmsKeyLifecyclePort, times(1)).createKey();
    // alias-level success audit for each alias
    assertThat(countProvisionAuditRows(true)).isEqualTo(2);
  }

  // ---------------------------------------------------------------------------
  // [E-21] Co-bind rejected when the cohort is not fully ACTIVE
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("[E-21] Co-bind 거부 — cohort 가 완전 ACTIVE 가 아니면 4xx + COHORT_NOT_ALL_ACTIVE 감사")
  void coBind_rejectedWhenCohortNotFullyActive() throws Exception {
    stubSuccessfulProvision();
    assertThat(provision("REWARD").getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(disableWallet(REWARD_ALIAS).getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<String> res = provision("SPONSOR");

    assertThat(res.getStatusCode().is4xxClientError()).isTrue();
    assertThat(countWalletRows(SPONSOR_ALIAS)).isZero();

    Map<String, Object> latest = latestProvisionAuditRow();
    assertThat(latest.get("success")).isEqualTo(false);
    assertThat(latest.get("wallet_alias")).isEqualTo(SPONSOR_ALIAS);
    assertThat(latest.get("failure_reason")).isEqualTo("COHORT_NOT_ALL_ACTIVE");
  }

  // ---------------------------------------------------------------------------
  // [E-30] Disable transitions the whole cohort, fires KMS DisableKey once
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("[E-30] Disable — cohort 전체가 DISABLED 로 전이되고 KMS DisableKey 는 한 번만 호출")
  void disable_transitionsWholeCohort_andFiresDisableKeyOnce() throws Exception {
    stubSuccessfulProvision();
    assertThat(provision("REWARD").getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(provision("SPONSOR").getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<String> res = disableWallet(REWARD_ALIAS);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(objectMapper.readTree(res.getBody()).at("/data/status").asText())
        .isEqualTo("DISABLED");

    // whole cohort transitioned, not just the trigger alias
    assertThat(walletStatus(REWARD_ALIAS)).isEqualTo("DISABLED");
    assertThat(walletStatus(SPONSOR_ALIAS)).isEqualTo("DISABLED");

    // KMS DisableKey fires exactly once per cohort
    verify(kmsKeyLifecyclePort, times(1)).disableKey(MOCK_KMS_KEY_ID);
    assertThat(countKmsAuditRows("KMS_DISABLE", true)).isEqualTo(1);
  }

  // ---------------------------------------------------------------------------
  // [E-31] Disable rejected on a mixed-state cohort
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("[E-31] Disable 거부 — mixed-state cohort 는 409 + COHORT_STATE_INCONSISTENT 감사")
  void disable_rejectedOnMixedStateCohort() {
    seedWalletRow(REWARD_ALIAS, DERIVED_ADDRESS, MOCK_KMS_KEY_ID, "ACTIVE");
    seedWalletRow(SPONSOR_ALIAS, DERIVED_ADDRESS, MOCK_KMS_KEY_ID, "DISABLED");

    ResponseEntity<String> res = disableWallet(REWARD_ALIAS);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    // nothing changed
    assertThat(walletStatus(REWARD_ALIAS)).isEqualTo("ACTIVE");
    assertThat(walletStatus(SPONSOR_ALIAS)).isEqualTo("DISABLED");

    Map<String, Object> latest = latestProvisionAuditRow();
    assertThat(latest.get("success")).isEqualTo(false);
    assertThat(latest.get("failure_reason")).isEqualTo("COHORT_STATE_INCONSISTENT");
    verify(kmsKeyLifecyclePort, never()).disableKey(anyString());
  }

  // ---------------------------------------------------------------------------
  // [E-32] Archive transitions the whole DISABLED cohort, schedules deletion once
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("[E-32] Archive — DISABLED cohort 전체가 ARCHIVED 로 전이되고 ScheduleKeyDeletion(30) 한 번")
  void archive_transitionsWholeDisabledCohort_andSchedulesDeletionOnce() throws Exception {
    stubSuccessfulProvision();
    assertThat(provision("REWARD").getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(provision("SPONSOR").getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(disableWallet(REWARD_ALIAS).getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<String> res = archiveWallet(REWARD_ALIAS);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(objectMapper.readTree(res.getBody()).at("/data/status").asText())
        .isEqualTo("ARCHIVED");

    assertThat(walletStatus(REWARD_ALIAS)).isEqualTo("ARCHIVED");
    assertThat(walletStatus(SPONSOR_ALIAS)).isEqualTo("ARCHIVED");

    // KMS ScheduleKeyDeletion fires exactly once per cohort, with the 30-day window
    verify(kmsKeyLifecyclePort, times(1)).scheduleKeyDeletion(MOCK_KMS_KEY_ID, 30);
    assertThat(countKmsAuditRows("KMS_SCHEDULE_DELETION", true)).isEqualTo(1);
  }

  // ---------------------------------------------------------------------------
  // [E-33] Archive rejected when the cohort is not uniformly DISABLED
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[E-33] Archive 거부 — cohort 가 uniformly DISABLED 가 아니면 409 + COHORT_STATE_INCONSISTENT")
  void archive_rejectedWhenCohortNotUniformlyDisabled() {
    seedWalletRow(REWARD_ALIAS, DERIVED_ADDRESS, MOCK_KMS_KEY_ID, "DISABLED");
    seedWalletRow(SPONSOR_ALIAS, DERIVED_ADDRESS, MOCK_KMS_KEY_ID, "ACTIVE");

    ResponseEntity<String> res = archiveWallet(REWARD_ALIAS);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(walletStatus(REWARD_ALIAS)).isEqualTo("DISABLED");
    assertThat(walletStatus(SPONSOR_ALIAS)).isEqualTo("ACTIVE");

    Map<String, Object> latest = latestProvisionAuditRow();
    assertThat(latest.get("success")).isEqualTo(false);
    assertThat(latest.get("failure_reason")).isEqualTo("COHORT_STATE_INCONSISTENT");
    verify(kmsKeyLifecyclePort, never()).scheduleKeyDeletion(anyString(), anyInt());
  }
}
