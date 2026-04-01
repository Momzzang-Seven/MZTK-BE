package momzzangseven.mztkbe.integration.e2e.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import momzzangseven.mztkbe.modules.auth.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.auth.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Login walletAddress field E2E test.
 *
 * <p>Verifies that the login response includes the correct {@code walletAddress} value when the
 * authenticated user has an ACTIVE wallet registered in {@code user_wallets}. Also verifies that
 * {@code walletAddress} is null when no ACTIVE wallet exists.
 *
 * <p>Prerequisites:
 *
 * <ul>
 *   <li>Local PostgreSQL running (see application-integration.yml)
 *   <li>Flyway migrations applied automatically
 * </ul>
 *
 * <p>The wallet row is inserted directly via JdbcTemplate to avoid the signature-verification logic
 * in {@code RegisterWalletService}.
 */
@Tag("e2e")
@ActiveProfiles("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Login walletAddress E2E test")
class LoginWalletAddressE2ETest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;

  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  private String baseUrl;
  private final List<String> createdUserEmails = new ArrayList<>();

  @BeforeEach
  void setUp() {
    baseUrl = "http://localhost:" + port;
  }

  @AfterEach
  void tearDown() {
    for (String email : createdUserEmails) {
      jdbcTemplate.update(
          "DELETE FROM user_wallets WHERE user_id = (SELECT id FROM users WHERE email = ?)", email);
      jdbcTemplate.update(
          "DELETE FROM refresh_tokens WHERE user_id = (SELECT id FROM users WHERE email = ?)",
          email);
      jdbcTemplate.update(
          "DELETE FROM user_progress WHERE user_id = (SELECT id FROM users WHERE email = ?)",
          email);
      jdbcTemplate.update("DELETE FROM users WHERE email = ?", email);
    }
    createdUserEmails.clear();
  }

  // ============================================================
  // Helpers
  // ============================================================

  private static String uniqueEmail() {
    return "e2e-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10) + "@example.com";
  }

  private static String uniqueWalletAddress() {
    // UUID without hyphens is 32 hex chars; pad with a second UUID's first 8 chars to reach 40.
    String first = UUID.randomUUID().toString().replace("-", "");
    String second = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    return "0x" + first + second;
  }

  private ResponseEntity<String> signup(String email, String password, String nickname) {
    createdUserEmails.add(email);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    Map<String, String> body = Map.of("email", email, "password", password, "nickname", nickname);
    return restTemplate.exchange(
        baseUrl + "/auth/signup", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
  }

  private ResponseEntity<String> login(String email, String password) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    Map<String, Object> body = Map.of("provider", "LOCAL", "email", email, "password", password);
    return restTemplate.exchange(
        baseUrl + "/auth/login", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
  }

  /**
   * Insert an ACTIVE wallet row directly into {@code user_wallets} for the given user. Bypasses
   * signature verification in {@code RegisterWalletService}.
   */
  private void insertActiveWallet(String email, String walletAddress) {
    jdbcTemplate.update(
        """
        INSERT INTO user_wallets (user_id, wallet_address, status, registered_at, created_at, updated_at)
        VALUES (
          (SELECT id FROM users WHERE email = ?),
          ?,
          'ACTIVE',
          NOW(),
          NOW(),
          NOW()
        )
        """,
        email,
        walletAddress);
  }

  // ============================================================
  // Tests
  // ============================================================

  @Test
  @DisplayName("walletAddress is returned in login response when user has an ACTIVE wallet")
  void login_withActiveWallet_returnsWalletAddress() throws Exception {
    String email = uniqueEmail();
    String walletAddress = uniqueWalletAddress();

    signup(email, "Test@1234!", "WalletUser");
    insertActiveWallet(email, walletAddress);

    ResponseEntity<String> loginResponse = login(email, "Test@1234!");

    assertThat(loginResponse.getStatusCode().is2xxSuccessful()).isTrue();

    JsonNode body = objectMapper.readTree(loginResponse.getBody());
    assertThat(body.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(body.at("/data/userInfo/walletAddress").asText()).isEqualTo(walletAddress);
  }

  @Test
  @DisplayName("walletAddress is null in login response when user has no registered wallet")
  void login_withNoWallet_returnsNullWalletAddress() throws Exception {
    String email = uniqueEmail();

    signup(email, "Test@1234!", "NoWalletUser");

    ResponseEntity<String> loginResponse = login(email, "Test@1234!");

    assertThat(loginResponse.getStatusCode().is2xxSuccessful()).isTrue();

    JsonNode body = objectMapper.readTree(loginResponse.getBody());
    assertThat(body.at("/status").asText()).isEqualTo("SUCCESS");
    // JsonNode.isMissingNode() or isNull() both indicate the field is absent/null
    JsonNode walletAddressNode = body.at("/data/userInfo/walletAddress");
    assertThat(walletAddressNode.isNull() || walletAddressNode.isMissingNode())
        .as("walletAddress should be null when user has no registered wallet")
        .isTrue();
  }
}
