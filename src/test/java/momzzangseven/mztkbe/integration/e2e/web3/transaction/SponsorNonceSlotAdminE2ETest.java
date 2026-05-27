package momzzangseven.mztkbe.integration.e2e.web3.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("[E2E] Sponsor nonce slot admin API")
class SponsorNonceSlotAdminE2ETest extends E2ETestBase {

  private static final long BASE_SEPOLIA_CHAIN_ID = 84532L;
  private static final String SPONSOR_ADDRESS = "0x" + "a".repeat(40);
  private static final String RECIPIENT_ADDRESS = "0x" + "b".repeat(40);

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PasswordEncoder passwordEncoder;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;

  @Test
  @DisplayName("[E2E-1] Admin can read sponsor nonce slots sorted by nonce")
  void adminCanReadSponsorNonceSlotsSortedByNonce() throws Exception {
    TestAdmin admin = createAdminAndLogin();
    SeededNonceSlot stuck = seedNonceSlot(51L, "STUCK", "RECEIPT_TIMEOUT_900S");
    SeededNonceSlot broadcasted = seedNonceSlot(52L, "BROADCASTED", null);

    ResponseEntity<String> response =
        getWithBearer(
            "/admin/web3/nonce-slots?chainId="
                + BASE_SEPOLIA_CHAIN_ID
                + "&fromAddress="
                + upperHexAddress(SPONSOR_ADDRESS),
            admin.accessToken());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.at("/status").asText()).isEqualTo("SUCCESS");
    JsonNode data = body.path("data");
    assertThat(data.at("/chainId").asLong()).isEqualTo(BASE_SEPOLIA_CHAIN_ID);
    assertThat(data.at("/fromAddress").asText()).isEqualTo(SPONSOR_ADDRESS);

    JsonNode slots = data.path("slots");
    assertThat(slots).hasSize(2);
    assertSlot(slots.get(0), stuck, "STUCK");
    assertSlot(slots.get(1), broadcasted, "BROADCASTED");
  }

  @Test
  @DisplayName("[E2E-2] Sponsor nonce slot admin API requires admin role")
  void nonceSlotAdminApiRequiresAdminRole() throws Exception {
    TestUser user = signupAndLogin("NonceUser");

    ResponseEntity<String> anonymousResponse =
        restTemplate.exchange(
            baseUrl()
                + "/admin/web3/nonce-slots?chainId="
                + BASE_SEPOLIA_CHAIN_ID
                + "&fromAddress="
                + SPONSOR_ADDRESS,
            HttpMethod.GET,
            new HttpEntity<>(jsonOnlyHeaders()),
            String.class);
    assertThat(anonymousResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    ResponseEntity<String> userResponse =
        getWithBearer(
            "/admin/web3/nonce-slots?chainId="
                + BASE_SEPOLIA_CHAIN_ID
                + "&fromAddress="
                + SPONSOR_ADDRESS,
            user.accessToken());
    assertThat(userResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("[E2E-3] Sponsor nonce migration indexes and constraints are valid")
  void sponsorNonceMigrationIndexesAndConstraintsAreValid() {
    assertThat(indexIsValid("idx_web3_tx_sender_nonce")).isTrue();
    assertThat(indexIsValid("uk_web3_tx_eip7702_authority_nonce")).isTrue();
    assertThat(indexIsValid("uk_web3_tx_non_reward_eip1559_sender_nonce")).isTrue();
    assertThat(indexIsValid("uk_web3_tx_id_chain_sender_nonce")).isTrue();
    assertThat(indexIsUnique("uk_web3_tx_eip7702_authority_nonce")).isTrue();
    assertThat(indexIsUnique("uk_web3_tx_non_reward_eip1559_sender_nonce")).isTrue();
    assertThat(indexIsUnique("uk_web3_tx_id_chain_sender_nonce")).isTrue();
    assertThat(constraintIsValidated("uk_web3_tx_id_chain_sender_nonce")).isTrue();
    assertThat(constraintIsValidated("ck_web3_tx_addresses_lower")).isTrue();
    assertThat(constraintIsValidated("ck_web3_nonce_state_from_lower")).isTrue();
    assertThat(constraintIsValidated("fk_web3_nonce_slot_attempt_tx_scope")).isTrue();
    assertThat(constraintIsValidated("fk_web3_nonce_slots_active_tx")).isTrue();
    assertThat(constraintIsValidated("fk_web3_nonce_slots_consumed_tx")).isTrue();
    assertThat(constraintIsValidated("fk_web3_nonce_slots_released_tx")).isTrue();
  }

  private TestAdmin createAdminAndLogin() throws Exception {
    String email = "admin-" + uniqueToken() + "@internal.mztk.local";
    jdbcTemplate.update(
        "INSERT INTO users (email, role, nickname, created_at, updated_at) "
            + "VALUES (?, 'ADMIN_GENERATED', 'NonceAdmin', NOW(), NOW())",
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

  private SeededNonceSlot seedNonceSlot(long nonce, String slotStatus, String terminalReason) {
    String txHash = randomTxHash();
    String idempotencyKey = "e2e:sponsor-nonce:" + uniqueToken() + ":" + nonce;
    Long txId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO web3_transactions (
                idempotency_key, reference_type, reference_id, from_address, to_address,
                amount_wei, nonce, status, tx_hash, signed_at, broadcasted_at, tx_type,
                chain_id, created_at, updated_at
            )
            VALUES (?, 'SERVER_TO_USER', ?, ?, ?, 0, ?, 'PENDING', ?, NOW(), NOW(),
                'EIP1559', ?, NOW(), NOW())
            RETURNING id
            """,
            Long.class,
            idempotencyKey,
            "nonce-slot-e2e-" + uniqueToken(),
            SPONSOR_ADDRESS,
            RECIPIENT_ADDRESS,
            nonce,
            txHash,
            BASE_SEPOLIA_CHAIN_ID);

    Long attemptId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO web3_nonce_slot_attempts (
                chain_id, from_address, nonce, attempt_no, tx_id, tx_hash, status,
                idempotency_key, terminal_reason, signed_at, broadcasted_at, created_at, updated_at
            )
            VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?, NOW(), NOW(), NOW(), NOW())
            RETURNING id
            """,
            Long.class,
            BASE_SEPOLIA_CHAIN_ID,
            SPONSOR_ADDRESS,
            nonce,
            txId,
            txHash,
            slotStatus,
            idempotencyKey + ":attempt:1",
            terminalReason);

    jdbcTemplate.update(
        """
        INSERT INTO web3_nonce_slots (
            chain_id, from_address, nonce, status, attempt_no, active_attempt_id,
            active_tx_id, active_tx_hash, stuck_reason, last_broadcasted_at,
            replacement_prepare_attempt_count, broadcast_recovery_attempt_count,
            created_at, updated_at
        )
        VALUES (?, ?, ?, ?, 1, ?, ?, ?, ?, NOW(), 0, 0, NOW(), NOW())
        """,
        BASE_SEPOLIA_CHAIN_ID,
        SPONSOR_ADDRESS,
        nonce,
        slotStatus,
        attemptId,
        txId,
        txHash,
        terminalReason);

    return new SeededNonceSlot(nonce, attemptId, txId, txHash);
  }

  private Long userIdByEmail(String email) {
    return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
  }

  private ResponseEntity<String> getWithBearer(String path, String accessToken) {
    return restTemplate.exchange(
        baseUrl() + path,
        HttpMethod.GET,
        new HttpEntity<>(bearerJsonHeaders(accessToken)),
        String.class);
  }

  private void assertSlot(JsonNode slot, SeededNonceSlot expected, String status) {
    assertThat(slot.at("/nonce").asLong()).isEqualTo(expected.nonce());
    assertThat(slot.at("/status").asText()).isEqualTo(status);
    assertThat(slot.at("/attemptNo").asInt()).isEqualTo(1);
    assertThat(slot.at("/activeAttemptId").asLong()).isEqualTo(expected.attemptId());
    assertThat(slot.at("/activeTxId").asLong()).isEqualTo(expected.txId());
    assertThat(slot.at("/activeTxHash").asText()).isEqualTo(expected.txHash());
    assertThat(slot.hasNonNull("updatedAt")).isTrue();
  }

  private boolean indexIsValid(String indexName) {
    Boolean valid =
        jdbcTemplate.queryForObject(
            """
            SELECT i.indisvalid AND i.indisready
            FROM pg_index i
            JOIN pg_class c ON c.oid = i.indexrelid
            WHERE c.relname = ?
            """,
            Boolean.class,
            indexName);
    return Boolean.TRUE.equals(valid);
  }

  private boolean indexIsUnique(String indexName) {
    Boolean unique =
        jdbcTemplate.queryForObject(
            """
            SELECT i.indisunique
            FROM pg_index i
            JOIN pg_class c ON c.oid = i.indexrelid
            WHERE c.relname = ?
            """,
            Boolean.class,
            indexName);
    return Boolean.TRUE.equals(unique);
  }

  private boolean constraintIsValidated(String constraintName) {
    Boolean validated =
        jdbcTemplate.queryForObject(
            """
            SELECT convalidated
            FROM pg_constraint
            WHERE conname = ?
            """,
            Boolean.class,
            constraintName);
    return Boolean.TRUE.equals(validated);
  }

  private static String uniqueToken() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  private static String randomTxHash() {
    return "0x" + uniqueToken() + uniqueToken();
  }

  private static String upperHexAddress(String address) {
    return "0x" + address.substring(2).toUpperCase();
  }

  private record TestAdmin(Long userId, String accessToken) {}

  private record SeededNonceSlot(long nonce, Long attemptId, Long txId, String txHash) {}
}
