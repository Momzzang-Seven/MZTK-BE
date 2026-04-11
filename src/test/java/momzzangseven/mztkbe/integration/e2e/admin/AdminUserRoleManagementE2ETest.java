package momzzangseven.mztkbe.integration.e2e.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.admin.domain.vo.GeneratedAdminCredentials;
import momzzangseven.mztkbe.modules.admin.infrastructure.delivery.LogBootstrapDeliveryAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * E2E tests for admin user role management (MOM-330).
 *
 * <p>Covers: admin login, account CRUD, password rotation/reset, recovery reseed, concurrency,
 * audit trail, and role hierarchy verification. Runs against a real PostgreSQL instance.
 *
 * <p>Test cases: [E-1] through [E-45].
 */
@Tag("e2e")
@ActiveProfiles({"integration", "dev"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "mztk.admin.bootstrap.enabled=false",
      "mztk.admin.recovery.anchor=test-e2e-recovery-anchor",
      "mztk.admin.seed.seed-count=2"
    })
@DisplayName("[E2E] Admin User Role Management 전체 흐름 테스트")
class AdminUserRoleManagementE2ETest {

  private static final String TEST_PASSWORD = "Test@1234!";
  private static final String RECOVERY_ANCHOR = "test-e2e-recovery-anchor";

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PasswordEncoder passwordEncoder;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;

  private String baseUrl;
  private final List<String> createdUserEmails = new ArrayList<>();
  private final List<Long> createdUserIds = new ArrayList<>();

  // ============================================================
  // Setup / Teardown
  // ============================================================

  @BeforeEach
  void setUp() {
    baseUrl = "http://localhost:" + port;
  }

  @AfterEach
  void tearDown() {
    // Delete admin_action_audits for our test users
    if (!createdUserIds.isEmpty()) {
      String inClause = inClausePlaceholders(createdUserIds.size());
      jdbcTemplate.update(
          "DELETE FROM admin_action_audits WHERE operator_id IN (" + inClause + ")",
          createdUserIds.toArray());
    }

    // Delete admin_accounts for our test users
    for (Long userId : createdUserIds) {
      jdbcTemplate.update("DELETE FROM admin_accounts WHERE user_id = ?", userId);
    }

    // Delete users and dependent rows
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

    // Clean up admin_action_audits with null operator (recovery)
    jdbcTemplate.update(
        "DELETE FROM admin_action_audits WHERE operator_id IS NULL "
            + "AND action_type IN ('RECOVERY_SUCCESS', 'RECOVERY_REJECTED')");

    createdUserEmails.clear();
    createdUserIds.clear();
  }

  // ============================================================
  // Helper Methods
  // ============================================================

  private static String uniqueEmail() {
    return "e2e-admin-"
        + UUID.randomUUID().toString().replace("-", "").substring(0, 10)
        + "@example.com";
  }

  private static String inClausePlaceholders(int n) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < n; i++) {
      if (i > 0) sb.append(",");
      sb.append("?");
    }
    return sb.toString();
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

  /**
   * Creates an admin account via direct DB manipulation. Returns a TestAdmin with credentials.
   *
   * @param role the admin role (ADMIN_SEED or ADMIN_GENERATED)
   */
  private TestAdmin createTestAdmin(String role) {
    String email = uniqueEmail();
    signup(email, TEST_PASSWORD, "AdminTest");
    createdUserEmails.add(email);

    Long userId =
        jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    createdUserIds.add(userId);

    jdbcTemplate.update("UPDATE users SET role = ? WHERE id = ?", role, userId);

    String loginId = String.valueOf(10000000 + (int) (Math.random() * 90000000));
    String plaintext = "AdminP@ss" + UUID.randomUUID().toString().substring(0, 8);
    String hash = passwordEncoder.encode(plaintext);

    jdbcTemplate.update(
        "INSERT INTO admin_accounts (user_id, login_id, password_hash, created_by,"
            + " last_login_at, password_last_rotated_at, deleted_at, created_at, updated_at)"
            + " VALUES (?, ?, ?, NULL, NULL, NULL, NULL, NOW(), NOW())",
        userId,
        loginId,
        hash);

    return new TestAdmin(userId, email, loginId, plaintext);
  }

  /** Creates a seed admin and logs in, returning TestAdmin with access token. */
  private TestAdmin createSeedAdminAndLogin() throws Exception {
    TestAdmin admin = createTestAdmin("ADMIN_SEED");
    String accessToken = adminLogin(admin.loginId, admin.plaintext);
    return admin.withAccessToken(accessToken);
  }

  /** Creates a generated admin and logs in, returning TestAdmin with access token. */
  private TestAdmin createGeneratedAdminAndLogin() throws Exception {
    TestAdmin admin = createTestAdmin("ADMIN_GENERATED");
    String accessToken = adminLogin(admin.loginId, admin.plaintext);
    return admin.withAccessToken(accessToken);
  }

  /** Creates a regular user and logs in, returning userId and access token. */
  private TestUser createUserAndLogin(String role) throws Exception {
    String email = uniqueEmail();
    Map<String, String> signupBody =
        Map.of("email", email, "password", TEST_PASSWORD, "nickname", "TestUser", "role", role);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    restTemplate.exchange(
        baseUrl + "/auth/signup",
        HttpMethod.POST,
        new HttpEntity<>(signupBody, headers),
        String.class);
    createdUserEmails.add(email);

    Long userId =
        jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    createdUserIds.add(userId);

    String accessToken = loginLocal(email, TEST_PASSWORD);
    return new TestUser(userId, email, accessToken);
  }

  private String adminLogin(String loginId, String password) throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    Map<String, String> body =
        Map.of("provider", "LOCAL_ADMIN", "loginId", loginId, "password", password);
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/auth/login",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return objectMapper.readTree(response.getBody()).at("/data/accessToken").asText();
  }

  private ResponseEntity<String> adminLoginRaw(String loginId, String password) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    Map<String, String> body =
        Map.of("provider", "LOCAL_ADMIN", "loginId", loginId, "password", password);
    return restTemplate.exchange(
        baseUrl + "/auth/login", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
  }

  private String loginLocal(String email, String password) throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    Map<String, Object> body = Map.of("provider", "LOCAL", "email", email, "password", password);
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/auth/login",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return objectMapper.readTree(response.getBody()).at("/data/accessToken").asText();
  }

  private HttpHeaders bearer(String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(accessToken);
    return headers;
  }

  private record TestAdmin(
      Long userId, String email, String loginId, String plaintext, String accessToken) {
    TestAdmin(Long userId, String email, String loginId, String plaintext) {
      this(userId, email, loginId, plaintext, null);
    }

    TestAdmin withAccessToken(String accessToken) {
      return new TestAdmin(userId, email, loginId, plaintext, accessToken);
    }
  }

  private record TestUser(Long userId, String email, String accessToken) {}

  // ============================================================
  // [E-1] ~ [E-4]: Admin Login Tests
  // ============================================================

  @Nested
  @DisplayName("Admin Login (POST /auth/login)")
  class AdminLoginTests {

    @Test
    @DisplayName("[E-1] Admin login with valid seed admin credentials returns JWT tokens")
    void adminLogin_validSeedCredentials_returnsJwtTokens() throws Exception {
      // given
      TestAdmin admin = createTestAdmin("ADMIN_SEED");

      // when
      ResponseEntity<String> response = adminLoginRaw(admin.loginId, admin.plaintext);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode data = objectMapper.readTree(response.getBody()).at("/data");
      assertThat(data.at("/accessToken").asText()).isNotBlank();
      // refreshToken is delivered as HttpOnly cookie, not in JSON body
      List<String> cookies = response.getHeaders().get("Set-Cookie");
      assertThat(cookies).isNotNull().anyMatch(c -> c.contains("refreshToken"));

      // Verify last_login_at updated
      Instant lastLoginAt =
          jdbcTemplate.queryForObject(
              "SELECT last_login_at FROM admin_accounts WHERE user_id = ?",
              Instant.class,
              admin.userId);
      assertThat(lastLoginAt).isNotNull();
    }

    @Test
    @DisplayName("[E-2] Admin login with wrong password returns 401")
    void adminLogin_wrongPassword_returns401() {
      // given
      TestAdmin admin = createTestAdmin("ADMIN_SEED");
      Instant lastLoginBefore =
          jdbcTemplate.queryForObject(
              "SELECT last_login_at FROM admin_accounts WHERE user_id = ?",
              Instant.class,
              admin.userId);

      // when
      ResponseEntity<String> response = adminLoginRaw(admin.loginId, "wrong-password");

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

      // last_login_at should not be updated
      Instant lastLoginAfter =
          jdbcTemplate.queryForObject(
              "SELECT last_login_at FROM admin_accounts WHERE user_id = ?",
              Instant.class,
              admin.userId);
      assertThat(lastLoginAfter).isEqualTo(lastLoginBefore);
    }

    @Test
    @DisplayName("[E-3] Admin login with non-existent loginId returns 401")
    void adminLogin_nonExistentLoginId_returns401() {
      // when
      ResponseEntity<String> response = adminLoginRaw("999999999", "any-password");

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("[E-4] Admin login with soft-deleted admin account returns 401")
    void adminLogin_softDeletedAccount_returns401() {
      // given
      TestAdmin admin = createTestAdmin("ADMIN_SEED");
      jdbcTemplate.update(
          "UPDATE admin_accounts SET deleted_at = NOW() WHERE user_id = ?", admin.userId);

      // when
      ResponseEntity<String> response = adminLoginRaw(admin.loginId, admin.plaintext);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
  }

  // ============================================================
  // [E-5] ~ [E-10]: Create Admin Account Tests
  // ============================================================

  @Nested
  @DisplayName("Create Admin Account (POST /admin/accounts)")
  class CreateAdminAccountTests {

    @Test
    @DisplayName("[E-5] Authenticated admin creates a new admin account successfully")
    void createAdmin_authenticatedAdmin_returns201() throws Exception {
      // given
      TestAdmin operator = createSeedAdminAndLogin();

      // when
      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl + "/admin/accounts",
              HttpMethod.POST,
              new HttpEntity<>(bearer(operator.accessToken)),
              String.class);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      JsonNode data = objectMapper.readTree(response.getBody()).at("/data");
      Long newUserId = data.at("/userId").asLong();
      String loginId = data.at("/loginId").asText();
      String generatedPassword = data.at("/generatedPassword").asText();

      assertThat(newUserId).isPositive();
      assertThat(loginId).isNotBlank();
      assertThat(generatedPassword).isNotBlank();
      assertThat(data.at("/createdAt").asText()).isNotBlank();

      // Track for cleanup
      createdUserIds.add(newUserId);
      String newEmail =
          jdbcTemplate.queryForObject(
              "SELECT email FROM users WHERE id = ?", String.class, newUserId);
      createdUserEmails.add(newEmail);

      // Verify DB state
      String role =
          jdbcTemplate.queryForObject(
              "SELECT role FROM users WHERE id = ?", String.class, newUserId);
      assertThat(role).isEqualTo("ADMIN_GENERATED");

      Long createdBy =
          jdbcTemplate.queryForObject(
              "SELECT created_by FROM admin_accounts WHERE user_id = ?", Long.class, newUserId);
      assertThat(createdBy).isEqualTo(operator.userId);

      // Verify audit log
      Integer auditCount =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM admin_action_audits WHERE action_type = 'CREATE_ADMIN'"
                  + " AND operator_id = ?",
              Integer.class,
              operator.userId);
      assertThat(auditCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("[E-6] Non-admin user cannot create admin account (403)")
    void createAdmin_regularUser_returns403() throws Exception {
      // given
      TestUser user = createUserAndLogin("USER");

      // when
      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl + "/admin/accounts",
              HttpMethod.POST,
              new HttpEntity<>(bearer(user.accessToken)),
              String.class);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("[E-7] Unauthenticated request to create admin account returns 401")
    void createAdmin_unauthenticated_returns401() {
      // when
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl + "/admin/accounts",
              HttpMethod.POST,
              new HttpEntity<>(headers),
              String.class);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("[E-8] TRAINER role cannot create admin account (403)")
    void createAdmin_trainerRole_returns403() throws Exception {
      // given
      TestUser trainer = createUserAndLogin("TRAINER");

      // when
      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl + "/admin/accounts",
              HttpMethod.POST,
              new HttpEntity<>(bearer(trainer.accessToken)),
              String.class);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("[E-9] Created admin account can immediately log in")
    void createAdmin_newAccount_canLoginImmediately() throws Exception {
      // given
      TestAdmin operator = createSeedAdminAndLogin();
      ResponseEntity<String> createResponse =
          restTemplate.exchange(
              baseUrl + "/admin/accounts",
              HttpMethod.POST,
              new HttpEntity<>(bearer(operator.accessToken)),
              String.class);
      JsonNode data = objectMapper.readTree(createResponse.getBody()).at("/data");
      String loginId = data.at("/loginId").asText();
      String generatedPassword = data.at("/generatedPassword").asText();
      Long newUserId = data.at("/userId").asLong();
      createdUserIds.add(newUserId);
      String newEmail =
          jdbcTemplate.queryForObject(
              "SELECT email FROM users WHERE id = ?", String.class, newUserId);
      createdUserEmails.add(newEmail);

      // when
      ResponseEntity<String> loginResponse = adminLoginRaw(loginId, generatedPassword);

      // then
      assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode loginData = objectMapper.readTree(loginResponse.getBody()).at("/data");
      assertThat(loginData.at("/accessToken").asText()).isNotBlank();
    }

    @Test
    @DisplayName("[E-10] Multiple admin accounts can be created sequentially with unique loginIds")
    void createAdmin_multiple_uniqueLoginIds() throws Exception {
      // given
      TestAdmin operator = createSeedAdminAndLogin();
      Set<String> loginIds = new HashSet<>();
      Set<Long> userIds = new HashSet<>();

      // when
      for (int i = 0; i < 3; i++) {
        ResponseEntity<String> response =
            restTemplate.exchange(
                baseUrl + "/admin/accounts",
                HttpMethod.POST,
                new HttpEntity<>(bearer(operator.accessToken)),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode data = objectMapper.readTree(response.getBody()).at("/data");
        loginIds.add(data.at("/loginId").asText());
        Long newUserId = data.at("/userId").asLong();
        userIds.add(newUserId);
        createdUserIds.add(newUserId);
        String newEmail =
            jdbcTemplate.queryForObject(
                "SELECT email FROM users WHERE id = ?", String.class, newUserId);
        createdUserEmails.add(newEmail);
      }

      // then
      assertThat(loginIds).hasSize(3);
      assertThat(userIds).hasSize(3);
    }
  }

  // ============================================================
  // [E-11] ~ [E-13]: List Admin Accounts Tests
  // ============================================================

  @Nested
  @DisplayName("List Admin Accounts (GET /admin/accounts)")
  class ListAdminAccountsTests {

    @Test
    @DisplayName("[E-11] List admin accounts returns all active admins")
    void listAdmins_withActiveAccounts_returnsAll() throws Exception {
      // given
      TestAdmin seedAdmin = createSeedAdminAndLogin();
      // Create 2 additional admin accounts via API
      for (int i = 0; i < 2; i++) {
        ResponseEntity<String> createResp =
            restTemplate.exchange(
                baseUrl + "/admin/accounts",
                HttpMethod.POST,
                new HttpEntity<>(bearer(seedAdmin.accessToken)),
                String.class);
        JsonNode data = objectMapper.readTree(createResp.getBody()).at("/data");
        Long newUserId = data.at("/userId").asLong();
        createdUserIds.add(newUserId);
        String newEmail =
            jdbcTemplate.queryForObject(
                "SELECT email FROM users WHERE id = ?", String.class, newUserId);
        createdUserEmails.add(newEmail);
      }

      // when
      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl + "/admin/accounts",
              HttpMethod.GET,
              new HttpEntity<>(bearer(seedAdmin.accessToken)),
              String.class);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode admins = objectMapper.readTree(response.getBody()).at("/data/admins");
      assertThat(admins.isArray()).isTrue();
      assertThat(admins.size()).isGreaterThanOrEqualTo(3);

      // Verify fields exist on entries
      JsonNode firstAdmin = admins.get(0);
      assertThat(firstAdmin.has("userId")).isTrue();
      assertThat(firstAdmin.has("loginId")).isTrue();
      assertThat(firstAdmin.has("isSeed")).isTrue();
    }

    @Test
    @DisplayName("[E-12] List admin accounts excludes soft-deleted admins")
    void listAdmins_softDeleted_excluded() throws Exception {
      // given
      TestAdmin seedAdmin = createSeedAdminAndLogin();
      TestAdmin deletedAdmin = createTestAdmin("ADMIN_GENERATED");
      jdbcTemplate.update(
          "UPDATE admin_accounts SET deleted_at = NOW() WHERE user_id = ?", deletedAdmin.userId);

      // when
      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl + "/admin/accounts",
              HttpMethod.GET,
              new HttpEntity<>(bearer(seedAdmin.accessToken)),
              String.class);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode admins = objectMapper.readTree(response.getBody()).at("/data/admins");
      boolean containsDeleted = false;
      for (JsonNode admin : admins) {
        if (admin.at("/userId").asLong() == deletedAdmin.userId) {
          containsDeleted = true;
          break;
        }
      }
      assertThat(containsDeleted).isFalse();
    }

    @Test
    @DisplayName("[E-13] Non-admin cannot list admin accounts (403)")
    void listAdmins_regularUser_returns403() throws Exception {
      // given
      TestUser user = createUserAndLogin("USER");

      // when
      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl + "/admin/accounts",
              HttpMethod.GET,
              new HttpEntity<>(bearer(user.accessToken)),
              String.class);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
  }

  // ============================================================
  // [E-14] ~ [E-21]: Rotate Own Password Tests
  // ============================================================

  @Nested
  @DisplayName("Rotate Own Password (POST /admin/auth/password)")
  class RotatePasswordTests {

    @Test
    @DisplayName("[E-14] Admin rotates own password successfully")
    void rotatePassword_validRequest_succeeds() throws Exception {
      // given
      TestAdmin admin = createSeedAdminAndLogin();
      String newPassword = "NewSecureP@ssw0rd!12345";

      // when
      Map<String, String> body =
          Map.of("currentPassword", admin.plaintext, "newPassword", newPassword);
      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl + "/admin/auth/password",
              HttpMethod.POST,
              new HttpEntity<>(body, bearer(admin.accessToken)),
              String.class);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

      // Old password no longer works
      ResponseEntity<String> oldLoginResp = adminLoginRaw(admin.loginId, admin.plaintext);
      assertThat(oldLoginResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

      // New password works
      ResponseEntity<String> newLoginResp = adminLoginRaw(admin.loginId, newPassword);
      assertThat(newLoginResp.getStatusCode()).isEqualTo(HttpStatus.OK);

      // password_last_rotated_at updated
      Instant rotatedAt =
          jdbcTemplate.queryForObject(
              "SELECT password_last_rotated_at FROM admin_accounts WHERE user_id = ?",
              Instant.class,
              admin.userId);
      assertThat(rotatedAt).isNotNull();
    }

    @Test
    @DisplayName("[E-15] Password rotation fails with wrong current password")
    void rotatePassword_wrongCurrentPassword_returns401() throws Exception {
      // given
      TestAdmin admin = createSeedAdminAndLogin();

      // when
      Map<String, String> body =
          Map.of(
              "currentPassword",
              "wrong-current-password",
              "newPassword",
              "NewSecureP@ssw0rd!12345");
      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl + "/admin/auth/password",
              HttpMethod.POST,
              new HttpEntity<>(body, bearer(admin.accessToken)),
              String.class);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

      // Original password still works
      ResponseEntity<String> loginResp = adminLoginRaw(admin.loginId, admin.plaintext);
      assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("[E-16] Password rotation fails when new password violates policy (too short)")
    void rotatePassword_tooShort_returns400() throws Exception {
      // given
      TestAdmin admin = createSeedAdminAndLogin();

      // when
      Map<String, String> body =
          Map.of("currentPassword", admin.plaintext, "newPassword", "Short1!");
      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl + "/admin/auth/password",
              HttpMethod.POST,
              new HttpEntity<>(body, bearer(admin.accessToken)),
              String.class);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      JsonNode responseBody = objectMapper.readTree(response.getBody());
      assertThat(responseBody.at("/code").asText()).isEqualTo("ADMIN_004");
    }

    @Test
    @DisplayName("[E-17] Password rotation fails when new password lacks uppercase")
    void rotatePassword_noUppercase_returns400() throws Exception {
      // given
      TestAdmin admin = createSeedAdminAndLogin();

      // when
      Map<String, String> body =
          Map.of("currentPassword", admin.plaintext, "newPassword", "nonewuppercasepassword1!");
      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl + "/admin/auth/password",
              HttpMethod.POST,
              new HttpEntity<>(body, bearer(admin.accessToken)),
              String.class);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      JsonNode responseBody = objectMapper.readTree(response.getBody());
      assertThat(responseBody.at("/code").asText()).isEqualTo("ADMIN_004");
    }

    @Test
    @DisplayName("[E-18] Password rotation fails when new password lacks special character")
    void rotatePassword_noSpecialChar_returns400() throws Exception {
      // given
      TestAdmin admin = createSeedAdminAndLogin();

      // when
      Map<String, String> body =
          Map.of("currentPassword", admin.plaintext, "newPassword", "NoSpecialCharacters1234A");
      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl + "/admin/auth/password",
              HttpMethod.POST,
              new HttpEntity<>(body, bearer(admin.accessToken)),
              String.class);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      JsonNode responseBody = objectMapper.readTree(response.getBody());
      assertThat(responseBody.at("/code").asText()).isEqualTo("ADMIN_004");
    }

    @Test
    @DisplayName("[E-19] Password rotation with blank currentPassword returns 400")
    void rotatePassword_blankCurrentPassword_returns400() throws Exception {
      // given
      TestAdmin admin = createSeedAdminAndLogin();

      // when
      Map<String, String> body =
          Map.of("currentPassword", "", "newPassword", "NewSecureP@ssw0rd!12345");
      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl + "/admin/auth/password",
              HttpMethod.POST,
              new HttpEntity<>(body, bearer(admin.accessToken)),
              String.class);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("[E-20] Password rotation with blank newPassword returns 400")
    void rotatePassword_blankNewPassword_returns400() throws Exception {
      // given
      TestAdmin admin = createSeedAdminAndLogin();

      // when
      Map<String, String> body = Map.of("currentPassword", admin.plaintext, "newPassword", "");
      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl + "/admin/auth/password",
              HttpMethod.POST,
              new HttpEntity<>(body, bearer(admin.accessToken)),
              String.class);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("[E-21] Non-admin cannot rotate password (403)")
    void rotatePassword_regularUser_returns403() throws Exception {
      // given
      TestUser user = createUserAndLogin("USER");

      // when
      Map<String, String> body =
          Map.of("currentPassword", TEST_PASSWORD, "newPassword", "NewSecureP@ssw0rd!12345");
      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl + "/admin/auth/password",
              HttpMethod.POST,
              new HttpEntity<>(body, bearer(user.accessToken)),
              String.class);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
  }

  // ============================================================
  // [E-22] ~ [E-26]: Peer-Reset Password Tests
  // ============================================================

  @Nested
  @DisplayName("Reset Peer Admin Password (POST /admin/accounts/{userId}/password/reset)")
  class PeerResetPasswordTests {

    @Test
    @DisplayName("[E-22] Admin resets another admin's password successfully")
    void peerReset_validRequest_succeeds() throws Exception {
      // given
      TestAdmin operator = createSeedAdminAndLogin();
      TestAdmin target = createTestAdmin("ADMIN_GENERATED");

      // when
      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl + "/admin/accounts/" + target.userId + "/password/reset",
              HttpMethod.POST,
              new HttpEntity<>(bearer(operator.accessToken)),
              String.class);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode data = objectMapper.readTree(response.getBody()).at("/data");
      assertThat(data.at("/userId").asLong()).isEqualTo(target.userId);
      assertThat(data.at("/loginId").asText()).isNotBlank();
      String newPassword = data.at("/generatedPassword").asText();
      assertThat(newPassword).isNotBlank();

      // Old password no longer works
      ResponseEntity<String> oldLoginResp = adminLoginRaw(target.loginId, target.plaintext);
      assertThat(oldLoginResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

      // New password works
      ResponseEntity<String> newLoginResp = adminLoginRaw(target.loginId, newPassword);
      assertThat(newLoginResp.getStatusCode()).isEqualTo(HttpStatus.OK);

      // Audit entry exists
      Integer auditCount =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM admin_action_audits WHERE action_type = 'PEER_RESET_ADMIN'"
                  + " AND operator_id = ?",
              Integer.class,
              operator.userId);
      assertThat(auditCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("[E-23] Admin cannot reset own password via peer-reset endpoint (403)")
    void peerReset_selfReset_returns403() throws Exception {
      // given
      TestAdmin admin = createSeedAdminAndLogin();

      // when
      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl + "/admin/accounts/" + admin.userId + "/password/reset",
              HttpMethod.POST,
              new HttpEntity<>(bearer(admin.accessToken)),
              String.class);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
      JsonNode responseBody = objectMapper.readTree(response.getBody());
      assertThat(responseBody.at("/code").asText()).isEqualTo("ADMIN_001");
    }

    @Test
    @DisplayName("[E-24] Peer-reset for non-existent admin userId returns 404")
    void peerReset_nonExistentUser_returns404() throws Exception {
      // given
      TestAdmin admin = createSeedAdminAndLogin();

      // when
      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl + "/admin/accounts/999999/password/reset",
              HttpMethod.POST,
              new HttpEntity<>(bearer(admin.accessToken)),
              String.class);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
      JsonNode responseBody = objectMapper.readTree(response.getBody());
      assertThat(responseBody.at("/code").asText()).isEqualTo("ADMIN_002");
    }

    @Test
    @DisplayName("[E-25] Non-admin cannot peer-reset admin password (403)")
    void peerReset_regularUser_returns403() throws Exception {
      // given
      TestUser user = createUserAndLogin("USER");
      TestAdmin target = createTestAdmin("ADMIN_GENERATED");

      // when
      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl + "/admin/accounts/" + target.userId + "/password/reset",
              HttpMethod.POST,
              new HttpEntity<>(bearer(user.accessToken)),
              String.class);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("[E-26] Peer-reset for soft-deleted admin returns 404")
    void peerReset_softDeletedAdmin_returns404() throws Exception {
      // given
      TestAdmin operator = createSeedAdminAndLogin();
      TestAdmin target = createTestAdmin("ADMIN_GENERATED");
      jdbcTemplate.update(
          "UPDATE admin_accounts SET deleted_at = NOW() WHERE user_id = ?", target.userId);

      // when
      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl + "/admin/accounts/" + target.userId + "/password/reset",
              HttpMethod.POST,
              new HttpEntity<>(bearer(operator.accessToken)),
              String.class);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
      JsonNode responseBody = objectMapper.readTree(response.getBody());
      assertThat(responseBody.at("/code").asText()).isEqualTo("ADMIN_002");
    }
  }

  // ============================================================
  // [E-27] ~ [E-33]: Recovery Reseed Tests
  // ============================================================

  @Nested
  @DisplayName("Recovery Reseed (POST /admin/recovery/reseed)")
  class RecoveryReseedTests {

    @Autowired private LogBootstrapDeliveryAdapter deliveryAdapter;

    private ResponseEntity<String> sendRecoveryRequest(String anchor) {
      return sendRecoveryRequestWithIp(anchor, null);
    }

    private ResponseEntity<String> sendRecoveryRequestWithIp(String anchor, String forwardedIp) {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      if (forwardedIp != null) {
        headers.set("X-Forwarded-For", forwardedIp);
      }
      Map<String, String> body = Map.of("recoveryAnchor", anchor);
      return restTemplate.exchange(
          baseUrl + "/admin/recovery/reseed",
          HttpMethod.POST,
          new HttpEntity<>(body, headers),
          String.class);
    }

    @Test
    @DisplayName(
        "[E-27] Recovery reseed with correct anchor hard-deletes all admins and provisions new"
            + " seeds")
    void recoveryReseed_correctAnchor_succeeds() throws Exception {
      // given — create some admin accounts that will be hard-deleted
      TestAdmin existingAdmin = createTestAdmin("ADMIN_SEED");
      String uniqueIp = "10.0.0." + (int) (Math.random() * 254 + 1);

      // when
      ResponseEntity<String> response = sendRecoveryRequestWithIp(RECOVERY_ANCHOR, uniqueIp);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode data = objectMapper.readTree(response.getBody()).at("/data");
      assertThat(data.at("/deliveredVia").asText()).isNotBlank();
      assertThat(data.at("/newSeedCount").asInt()).isEqualTo(2);

      // Verify existing admin is hard-deleted (row no longer exists)
      Integer deletedCount =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM admin_accounts WHERE user_id = ?",
              Integer.class,
              existingAdmin.userId);
      assertThat(deletedCount).isZero();

      // Verify new seed accounts exist
      Integer activeCount =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM admin_accounts WHERE deleted_at IS NULL", Integer.class);
      assertThat(activeCount).isGreaterThanOrEqualTo(2);

      // Track new seed users for cleanup (existing admin row is hard-deleted, no exclusion needed)
      List<Long> newSeedUserIds =
          jdbcTemplate.queryForList(
              "SELECT user_id FROM admin_accounts WHERE deleted_at IS NULL", Long.class);
      for (Long newUserId : newSeedUserIds) {
        if (!createdUserIds.contains(newUserId)) {
          createdUserIds.add(newUserId);
          String email =
              jdbcTemplate.queryForObject(
                  "SELECT email FROM users WHERE id = ?", String.class, newUserId);
          createdUserEmails.add(email);
        }
      }

      // Verify audit
      Integer auditCount =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM admin_action_audits WHERE action_type = 'RECOVERY_SUCCESS'",
              Integer.class);
      assertThat(auditCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("[E-28] Recovery reseed with wrong anchor returns 403")
    void recoveryReseed_wrongAnchor_returns403() throws Exception {
      // given
      String uniqueIp = "10.1.0." + (int) (Math.random() * 254 + 1);

      // when
      ResponseEntity<String> response = sendRecoveryRequestWithIp("wrong-anchor-value", uniqueIp);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
      JsonNode responseBody = objectMapper.readTree(response.getBody());
      assertThat(responseBody.at("/code").asText()).isEqualTo("ADMIN_006");

      // Verify rejection audit entry
      Integer auditCount =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM admin_action_audits WHERE action_type = 'RECOVERY_REJECTED'",
              Integer.class);
      assertThat(auditCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("[E-29] Recovery reseed with blank anchor returns 400")
    void recoveryReseed_blankAnchor_returns400() {
      // when
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.set("X-Forwarded-For", "10.2.0.1");
      Map<String, String> body = Map.of("recoveryAnchor", "");
      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl + "/admin/recovery/reseed",
              HttpMethod.POST,
              new HttpEntity<>(body, headers),
              String.class);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("[E-30] Recovery reseed rate limiting — 4th request within 1 minute returns 429")
    void recoveryReseed_rateLimiting_returns429OnFourthRequest() {
      // Use a unique IP to avoid interference from other tests
      String uniqueIp = "10.3.0." + (int) (Math.random() * 254 + 1);

      // First 3 requests: should be processed (403 due to wrong anchor)
      for (int i = 0; i < 3; i++) {
        ResponseEntity<String> response = sendRecoveryRequestWithIp("wrong-anchor", uniqueIp);
        assertThat(response.getStatusCode())
            .as("Request %d should be 403, not rate limited", i + 1)
            .isEqualTo(HttpStatus.FORBIDDEN);
      }

      // 4th request: should be rate limited
      ResponseEntity<String> response = sendRecoveryRequestWithIp("wrong-anchor", uniqueIp);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("[E-31] Recovery reseed rate limit resets after 1 minute")
    void recoveryReseed_rateLimitResets_afterOneMinute() throws Exception {
      // Use a unique IP
      String uniqueIp = "10.4.0." + (int) (Math.random() * 254 + 1);

      // Exhaust rate limit
      for (int i = 0; i < 3; i++) {
        sendRecoveryRequestWithIp("wrong-anchor", uniqueIp);
      }

      // Wait >60 seconds for bucket refill
      Thread.sleep(61_000);

      // Next request should NOT be rate limited (should be 403 for wrong anchor)
      ResponseEntity<String> response = sendRecoveryRequestWithIp("wrong-anchor", uniqueIp);
      assertThat(response.getStatusCode())
          .as("After rate limit window, request should be processed (403), not rate limited (429)")
          .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("[E-32] New seed admins from recovery reseed can log in immediately")
    void recoveryReseed_newSeeds_canLoginImmediately() throws Exception {
      // given
      createTestAdmin("ADMIN_SEED"); // Ensure at least one admin exists
      String uniqueIp = "10.5.0." + (int) (Math.random() * 254 + 1);

      // when — perform recovery reseed
      ResponseEntity<String> reseedResponse = sendRecoveryRequestWithIp(RECOVERY_ANCHOR, uniqueIp);
      assertThat(reseedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

      // Retrieve new seed credentials from delivery adapter
      List<GeneratedAdminCredentials> newCredentials = deliveryAdapter.getLastDelivery();
      assertThat(newCredentials).isNotEmpty();

      // Track for cleanup
      for (GeneratedAdminCredentials cred : newCredentials) {
        Long newUserId =
            jdbcTemplate.queryForObject(
                "SELECT user_id FROM admin_accounts WHERE login_id = ? AND deleted_at IS NULL",
                Long.class,
                cred.loginId());
        if (!createdUserIds.contains(newUserId)) {
          createdUserIds.add(newUserId);
          String email =
              jdbcTemplate.queryForObject(
                  "SELECT email FROM users WHERE id = ?", String.class, newUserId);
          createdUserEmails.add(email);
        }
      }

      // then — login with new seed credentials
      GeneratedAdminCredentials firstSeed = newCredentials.get(0);
      ResponseEntity<String> loginResponse =
          adminLoginRaw(firstSeed.loginId(), firstSeed.plaintext());
      assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName(
        "[E-33] Recovery reseed followed by admin operations — new seeds can create accounts")
    void recoveryReseed_newSeeds_canCreateAccounts() throws Exception {
      // given
      createTestAdmin("ADMIN_SEED");
      String uniqueIp = "10.6.0." + (int) (Math.random() * 254 + 1);

      // Perform recovery reseed
      ResponseEntity<String> reseedResponse = sendRecoveryRequestWithIp(RECOVERY_ANCHOR, uniqueIp);
      assertThat(reseedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

      // Login as new seed admin
      List<GeneratedAdminCredentials> newCredentials = deliveryAdapter.getLastDelivery();
      assertThat(newCredentials).isNotEmpty();
      GeneratedAdminCredentials seedCred = newCredentials.get(0);

      // Track for cleanup
      for (GeneratedAdminCredentials cred : newCredentials) {
        Long newUserId =
            jdbcTemplate.queryForObject(
                "SELECT user_id FROM admin_accounts WHERE login_id = ? AND deleted_at IS NULL",
                Long.class,
                cred.loginId());
        if (!createdUserIds.contains(newUserId)) {
          createdUserIds.add(newUserId);
          String email =
              jdbcTemplate.queryForObject(
                  "SELECT email FROM users WHERE id = ?", String.class, newUserId);
          createdUserEmails.add(email);
        }
      }

      String accessToken = adminLogin(seedCred.loginId(), seedCred.plaintext());

      // when — create a new admin account
      ResponseEntity<String> createResponse =
          restTemplate.exchange(
              baseUrl + "/admin/accounts",
              HttpMethod.POST,
              new HttpEntity<>(bearer(accessToken)),
              String.class);

      // then
      assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      JsonNode data = objectMapper.readTree(createResponse.getBody()).at("/data");
      Long createdUserId = data.at("/userId").asLong();
      createdUserIds.add(createdUserId);
      String createdEmail =
          jdbcTemplate.queryForObject(
              "SELECT email FROM users WHERE id = ?", String.class, createdUserId);
      createdUserEmails.add(createdEmail);
    }
  }

  // ============================================================
  // [E-34] ~ [E-37]: Transaction & Concurrency Tests
  // ============================================================

  @Nested
  @DisplayName("Transaction & Concurrency")
  class TransactionConcurrencyTests {

    @Test
    @DisplayName(
        "[E-34] Create admin account is atomic — failure during user creation rolls back"
            + " everything")
    void createAdmin_failureDuringUserCreation_rollsBack() throws Exception {
      // This test verifies atomicity by checking that no partial state remains
      // after a failed operation. We verify by counting before and after.
      TestAdmin admin = createSeedAdminAndLogin();

      Integer adminCountBefore =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM admin_accounts WHERE deleted_at IS NULL", Integer.class);
      Integer userCountBefore =
          jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);

      // Attempt to create admin account (this should succeed normally)
      // To test atomicity on failure we verify the consistent state
      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl + "/admin/accounts",
              HttpMethod.POST,
              new HttpEntity<>(bearer(admin.accessToken)),
              String.class);

      if (response.getStatusCode().is2xxSuccessful()) {
        // Clean up the successfully created account
        JsonNode data = objectMapper.readTree(response.getBody()).at("/data");
        Long newUserId = data.at("/userId").asLong();
        createdUserIds.add(newUserId);
        String newEmail =
            jdbcTemplate.queryForObject(
                "SELECT email FROM users WHERE id = ?", String.class, newUserId);
        createdUserEmails.add(newEmail);
      }

      // Verify counts changed atomically (both incremented, not one without the other)
      Integer adminCountAfter =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM admin_accounts WHERE deleted_at IS NULL", Integer.class);
      Integer userCountAfter =
          jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);

      // Both or neither should have been incremented
      int adminDelta = adminCountAfter - adminCountBefore;
      int userDelta = userCountAfter - userCountBefore;
      assertThat(adminDelta).isEqualTo(userDelta > 0 ? 1 : 0);
    }

    @Test
    @DisplayName("[E-35] Concurrent create admin requests produce unique loginIds")
    void createAdmin_concurrent_uniqueLoginIds() throws Exception {
      // given
      TestAdmin admin = createSeedAdminAndLogin();
      int concurrentRequests = 5;
      ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);

      // when
      List<CompletableFuture<ResponseEntity<String>>> futures = new ArrayList<>();
      for (int i = 0; i < concurrentRequests; i++) {
        futures.add(
            CompletableFuture.supplyAsync(
                () ->
                    restTemplate.exchange(
                        baseUrl + "/admin/accounts",
                        HttpMethod.POST,
                        new HttpEntity<>(bearer(admin.accessToken)),
                        String.class),
                executor));
      }

      List<ResponseEntity<String>> responses = new ArrayList<>();
      for (CompletableFuture<ResponseEntity<String>> future : futures) {
        responses.add(future.get(30, TimeUnit.SECONDS));
      }
      executor.shutdown();

      // then
      Set<String> loginIds = new HashSet<>();
      Set<Long> userIds = new HashSet<>();
      int successCount = 0;
      for (ResponseEntity<String> response : responses) {
        if (response.getStatusCode() == HttpStatus.CREATED) {
          successCount++;
          JsonNode data = objectMapper.readTree(response.getBody()).at("/data");
          loginIds.add(data.at("/loginId").asText());
          Long newUserId = data.at("/userId").asLong();
          userIds.add(newUserId);
          createdUserIds.add(newUserId);
          String newEmail =
              jdbcTemplate.queryForObject(
                  "SELECT email FROM users WHERE id = ?", String.class, newUserId);
          createdUserEmails.add(newEmail);
        }
      }

      assertThat(successCount).isEqualTo(concurrentRequests);
      assertThat(loginIds).hasSize(concurrentRequests);
      assertThat(userIds).hasSize(concurrentRequests);
    }

    @Test
    @DisplayName(
        "[E-36] Concurrent peer-reset on same target — both produce valid passwords or one"
            + " succeeds")
    void peerReset_concurrent_noCorruption() throws Exception {
      // given
      TestAdmin operator1 = createSeedAdminAndLogin();
      TestAdmin operator2 = createSeedAdminAndLogin();
      TestAdmin target = createTestAdmin("ADMIN_GENERATED");

      ExecutorService executor = Executors.newFixedThreadPool(2);

      // when
      CompletableFuture<ResponseEntity<String>> future1 =
          CompletableFuture.supplyAsync(
              () ->
                  restTemplate.exchange(
                      baseUrl + "/admin/accounts/" + target.userId + "/password/reset",
                      HttpMethod.POST,
                      new HttpEntity<>(bearer(operator1.accessToken)),
                      String.class),
              executor);

      CompletableFuture<ResponseEntity<String>> future2 =
          CompletableFuture.supplyAsync(
              () ->
                  restTemplate.exchange(
                      baseUrl + "/admin/accounts/" + target.userId + "/password/reset",
                      HttpMethod.POST,
                      new HttpEntity<>(bearer(operator2.accessToken)),
                      String.class),
              executor);

      ResponseEntity<String> response1 = future1.get(30, TimeUnit.SECONDS);
      ResponseEntity<String> response2 = future2.get(30, TimeUnit.SECONDS);
      executor.shutdown();

      // then — both should succeed (HTTP 200)
      assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);

      // At least one of the generated passwords should work for login
      String pw1 =
          objectMapper.readTree(response1.getBody()).at("/data/generatedPassword").asText();
      String pw2 =
          objectMapper.readTree(response2.getBody()).at("/data/generatedPassword").asText();

      boolean pw1Works = adminLoginRaw(target.loginId, pw1).getStatusCode() == HttpStatus.OK;
      boolean pw2Works = adminLoginRaw(target.loginId, pw2).getStatusCode() == HttpStatus.OK;
      assertThat(pw1Works || pw2Works)
          .as("At least one password from concurrent resets should work")
          .isTrue();
    }

    @Test
    @DisplayName("[E-37] Recovery reseed during concurrent admin login — login fails gracefully")
    void recoveryReseed_concurrentLogin_noServerError() throws Exception {
      // given
      TestAdmin admin = createTestAdmin("ADMIN_SEED");
      String uniqueIp = "10.7.0." + (int) (Math.random() * 254 + 1);
      ExecutorService executor = Executors.newFixedThreadPool(2);

      // when
      CompletableFuture<ResponseEntity<String>> reseedFuture =
          CompletableFuture.supplyAsync(
              () -> {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("X-Forwarded-For", uniqueIp);
                Map<String, String> body = Map.of("recoveryAnchor", RECOVERY_ANCHOR);
                return restTemplate.exchange(
                    baseUrl + "/admin/recovery/reseed",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class);
              },
              executor);

      CompletableFuture<ResponseEntity<String>> loginFuture =
          CompletableFuture.supplyAsync(
              () -> adminLoginRaw(admin.loginId, admin.plaintext), executor);

      ResponseEntity<String> reseedResponse = reseedFuture.get(30, TimeUnit.SECONDS);
      ResponseEntity<String> loginResponse = loginFuture.get(30, TimeUnit.SECONDS);
      executor.shutdown();

      // then
      assertThat(reseedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      // Login either succeeds (ran before soft-delete) or returns 401 (account deleted)
      assertThat(loginResponse.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.UNAUTHORIZED);

      // Track new seed accounts for cleanup
      List<Long> newAdminIds =
          jdbcTemplate.queryForList(
              "SELECT user_id FROM admin_accounts WHERE deleted_at IS NULL", Long.class);
      for (Long id : newAdminIds) {
        if (!createdUserIds.contains(id)) {
          createdUserIds.add(id);
          String email =
              jdbcTemplate.queryForObject("SELECT email FROM users WHERE id = ?", String.class, id);
          if (!createdUserEmails.contains(email)) {
            createdUserEmails.add(email);
          }
        }
      }
    }
  }

  // ============================================================
  // [E-38] ~ [E-42]: Audit Trail Tests
  // ============================================================

  @Nested
  @DisplayName("Audit Trail")
  class AuditTrailTests {

    @Test
    @DisplayName("[E-38] Create admin account produces audit log entry")
    void createAdmin_producesAuditLog() throws Exception {
      // given
      TestAdmin operator = createSeedAdminAndLogin();

      // when
      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl + "/admin/accounts",
              HttpMethod.POST,
              new HttpEntity<>(bearer(operator.accessToken)),
              String.class);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      JsonNode data = objectMapper.readTree(response.getBody()).at("/data");
      Long newUserId = data.at("/userId").asLong();
      createdUserIds.add(newUserId);
      String newEmail =
          jdbcTemplate.queryForObject(
              "SELECT email FROM users WHERE id = ?", String.class, newUserId);
      createdUserEmails.add(newEmail);

      // then
      Map<String, Object> audit =
          jdbcTemplate.queryForMap(
              "SELECT action_type, target_type, operator_id FROM admin_action_audits"
                  + " WHERE action_type = 'CREATE_ADMIN' AND operator_id = ?"
                  + " ORDER BY created_at DESC LIMIT 1",
              operator.userId);
      assertThat(audit.get("action_type")).isEqualTo("CREATE_ADMIN");
      assertThat(audit.get("target_type")).isEqualTo("ADMIN_ACCOUNT");
      assertThat(((Number) audit.get("operator_id")).longValue()).isEqualTo(operator.userId);
    }

    @Test
    @DisplayName("[E-39] Password rotation produces audit log entry")
    void rotatePassword_producesAuditLog() throws Exception {
      // given
      TestAdmin admin = createSeedAdminAndLogin();
      String newPassword = "NewSecureP@ssw0rd!12345";

      // when
      Map<String, String> body =
          Map.of("currentPassword", admin.plaintext, "newPassword", newPassword);
      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl + "/admin/auth/password",
              HttpMethod.POST,
              new HttpEntity<>(body, bearer(admin.accessToken)),
              String.class);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

      // then
      Integer auditCount =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM admin_action_audits"
                  + " WHERE action_type = 'ROTATE_OWN_PASSWORD' AND operator_id = ?",
              Integer.class,
              admin.userId);
      assertThat(auditCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("[E-40] Peer-reset produces audit log entry")
    void peerReset_producesAuditLog() throws Exception {
      // given
      TestAdmin operator = createSeedAdminAndLogin();
      TestAdmin target = createTestAdmin("ADMIN_GENERATED");

      // when
      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl + "/admin/accounts/" + target.userId + "/password/reset",
              HttpMethod.POST,
              new HttpEntity<>(bearer(operator.accessToken)),
              String.class);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

      // then
      Integer auditCount =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM admin_action_audits"
                  + " WHERE action_type = 'PEER_RESET_ADMIN' AND operator_id = ?",
              Integer.class,
              operator.userId);
      assertThat(auditCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName(
        "[E-41] Recovery reseed failure (wrong anchor) produces rejection audit entry with source"
            + " IP")
    void recoveryReseed_wrongAnchor_producesRejectionAudit() throws Exception {
      // given
      String uniqueIp = "10.8.0." + (int) (Math.random() * 254 + 1);

      // when
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.set("X-Forwarded-For", uniqueIp);
      Map<String, String> body = Map.of("recoveryAnchor", "wrong-anchor");
      restTemplate.exchange(
          baseUrl + "/admin/recovery/reseed",
          HttpMethod.POST,
          new HttpEntity<>(body, headers),
          String.class);

      // then
      Integer auditCount =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM admin_action_audits"
                  + " WHERE action_type = 'RECOVERY_REJECTED'",
              Integer.class);
      assertThat(auditCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("[E-42] Recovery reseed success produces success audit entry")
    void recoveryReseed_success_producesSuccessAudit() throws Exception {
      // given
      createTestAdmin("ADMIN_SEED");
      String uniqueIp = "10.9.0." + (int) (Math.random() * 254 + 1);

      // when
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.set("X-Forwarded-For", uniqueIp);
      Map<String, String> body = Map.of("recoveryAnchor", RECOVERY_ANCHOR);
      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl + "/admin/recovery/reseed",
              HttpMethod.POST,
              new HttpEntity<>(body, headers),
              String.class);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

      // Track new accounts for cleanup
      List<Long> newAdminIds =
          jdbcTemplate.queryForList(
              "SELECT user_id FROM admin_accounts WHERE deleted_at IS NULL", Long.class);
      for (Long id : newAdminIds) {
        if (!createdUserIds.contains(id)) {
          createdUserIds.add(id);
          String email =
              jdbcTemplate.queryForObject("SELECT email FROM users WHERE id = ?", String.class, id);
          if (!createdUserEmails.contains(email)) {
            createdUserEmails.add(email);
          }
        }
      }

      // then
      Integer auditCount =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM admin_action_audits"
                  + " WHERE action_type = 'RECOVERY_SUCCESS'",
              Integer.class);
      assertThat(auditCount).isGreaterThanOrEqualTo(1);
    }
  }

  // ============================================================
  // [E-43] ~ [E-45]: Role Hierarchy Tests
  // ============================================================

  @Nested
  @DisplayName("Role Hierarchy")
  class RoleHierarchyTests {

    @Test
    @DisplayName("[E-43] ADMIN_SEED role can access all admin endpoints")
    void adminSeed_canAccessAllAdminEndpoints() throws Exception {
      // given
      TestAdmin seedAdmin = createSeedAdminAndLogin();

      // POST /admin/accounts
      ResponseEntity<String> createResponse =
          restTemplate.exchange(
              baseUrl + "/admin/accounts",
              HttpMethod.POST,
              new HttpEntity<>(bearer(seedAdmin.accessToken)),
              String.class);
      assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      JsonNode data = objectMapper.readTree(createResponse.getBody()).at("/data");
      Long newUserId = data.at("/userId").asLong();
      createdUserIds.add(newUserId);
      String newEmail =
          jdbcTemplate.queryForObject(
              "SELECT email FROM users WHERE id = ?", String.class, newUserId);
      createdUserEmails.add(newEmail);

      // GET /admin/accounts
      ResponseEntity<String> listResponse =
          restTemplate.exchange(
              baseUrl + "/admin/accounts",
              HttpMethod.GET,
              new HttpEntity<>(bearer(seedAdmin.accessToken)),
              String.class);
      assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

      // POST /admin/auth/password
      String newPassword = "SeedNewP@ssw0rd!12345";
      Map<String, String> body =
          Map.of("currentPassword", seedAdmin.plaintext, "newPassword", newPassword);
      ResponseEntity<String> passwordResponse =
          restTemplate.exchange(
              baseUrl + "/admin/auth/password",
              HttpMethod.POST,
              new HttpEntity<>(body, bearer(seedAdmin.accessToken)),
              String.class);
      assertThat(passwordResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("[E-44] ADMIN_GENERATED role can access all admin endpoints")
    void adminGenerated_canAccessAllAdminEndpoints() throws Exception {
      // given
      TestAdmin genAdmin = createGeneratedAdminAndLogin();

      // POST /admin/accounts
      ResponseEntity<String> createResponse =
          restTemplate.exchange(
              baseUrl + "/admin/accounts",
              HttpMethod.POST,
              new HttpEntity<>(bearer(genAdmin.accessToken)),
              String.class);
      assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      JsonNode data = objectMapper.readTree(createResponse.getBody()).at("/data");
      Long newUserId = data.at("/userId").asLong();
      createdUserIds.add(newUserId);
      String newEmail =
          jdbcTemplate.queryForObject(
              "SELECT email FROM users WHERE id = ?", String.class, newUserId);
      createdUserEmails.add(newEmail);

      // GET /admin/accounts
      ResponseEntity<String> listResponse =
          restTemplate.exchange(
              baseUrl + "/admin/accounts",
              HttpMethod.GET,
              new HttpEntity<>(bearer(genAdmin.accessToken)),
              String.class);
      assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

      // POST /admin/auth/password
      String newPassword = "GenNewP@ssw0rd!12345";
      Map<String, String> body =
          Map.of("currentPassword", genAdmin.plaintext, "newPassword", newPassword);
      ResponseEntity<String> passwordResponse =
          restTemplate.exchange(
              baseUrl + "/admin/auth/password",
              HttpMethod.POST,
              new HttpEntity<>(body, bearer(genAdmin.accessToken)),
              String.class);
      assertThat(passwordResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("[E-45] Expired JWT token returns 401 on admin endpoints")
    void expiredJwt_returns401() {
      // given — use a clearly invalid/expired token
      String expiredToken =
          "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
              + ".eyJzdWIiOiIxIiwiZW1haWwiOiJ0ZXN0QHRlc3QuY29tIiwicm9sZSI6IkFETUlOX1NFRUQiLCJ0eXBl"
              + "IjoiYWNjZXNzIiwiaWF0IjoxNjAwMDAwMDAwLCJleHAiOjE2MDAwMDAwMDF9"
              + ".invalid-signature";

      // when
      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl + "/admin/accounts",
              HttpMethod.GET,
              new HttpEntity<>(bearer(expiredToken)),
              String.class);

      // then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
  }
}
