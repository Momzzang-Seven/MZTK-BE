package momzzangseven.mztkbe.integration.e2e.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Common base class for E2E tests. Provides:
 *
 * <ul>
 *   <li>Standard {@code @SpringBootTest} + {@code integration} profile wiring.
 *   <li>Automatic database cleanup after every test via {@link DatabaseCleaner}.
 *   <li>Opt-in re-seeding of bootstrap admin accounts for tests that need them.
 *   <li>Shared HTTP helpers for the signup / login / access-token flow.
 * </ul>
 *
 * <p>By default the seed admin bootstrap is disabled ({@code mztk.admin.bootstrap.enabled=false}).
 * Subclasses that need bootstrap-provisioned admins should override {@link
 * #requiresBootstrapSeedAdmins()} to return {@code true} and enable the property via their own
 * {@code @TestPropertySource}.
 */
@Tag("e2e")
@ActiveProfiles("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "mztk.admin.bootstrap.enabled=false")
public abstract class E2ETestBase {

  /** Default password applied when callers omit one. Meets local password policy. */
  protected static final String DEFAULT_TEST_PASSWORD = "Test@1234!";

  @Autowired protected DatabaseCleaner databaseCleaner;

  @LocalServerPort protected int port;

  @Autowired protected TestRestTemplate restTemplate;

  @Autowired protected ObjectMapper objectMapper;

  @AfterEach
  void cleanDatabaseAfterTest() {
    databaseCleaner.clean();
    if (requiresBootstrapSeedAdmins()) {
      databaseCleaner.reseedBootstrapAdmins();
    }
  }

  /**
   * Override and return {@code true} in tests that rely on seed admin accounts being present. The
   * base class will re-run the bootstrap use case after each cleanup so the next test starts with
   * the seed admins restored. Such tests must also enable {@code mztk.admin.bootstrap.enabled=true}
   * in their own {@code @TestPropertySource} so the initial startup run populates them.
   */
  protected boolean requiresBootstrapSeedAdmins() {
    return false;
  }

  // ===================================================================
  // HTTP helpers — base URL, headers
  // ===================================================================

  /** Base URL of the running Spring Boot instance. Safe to call after context startup. */
  protected String baseUrl() {
    return "http://localhost:" + port;
  }

  /** Generates a short unique email suitable for signup. Collision-free across concurrent tests. */
  protected static String randomEmail() {
    return "e2e-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10) + "@test.com";
  }

  /** JSON + Bearer Authorization headers for the given access token. */
  protected HttpHeaders bearerJsonHeaders(String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(accessToken);
    return headers;
  }

  /** JSON headers with no authentication (for anonymous flows). */
  protected HttpHeaders jsonOnlyHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  // ===================================================================
  // Signup / Login flow
  // ===================================================================

  /**
   * Result of the shared signup-and-login flow. Exposes the fields that individual tests most
   * commonly need — tests that only care about one field can ignore the rest.
   */
  public record TestUser(Long userId, String email, String password, String accessToken) {}

  /**
   * Calls {@code POST /auth/signup} and returns the newly created {@code userId} extracted from
   * {@code /data/userId} in the response body.
   */
  protected Long signupUser(String email, String password, String nickname) {
    Map<String, String> body = Map.of("email", email, "password", password, "nickname", nickname);
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/auth/signup",
            HttpMethod.POST,
            new HttpEntity<>(body, jsonOnlyHeaders()),
            String.class);
    if (!response.getStatusCode().is2xxSuccessful()) {
      throw new IllegalStateException(
          "signup failed: status=" + response.getStatusCode() + ", body=" + response.getBody());
    }
    try {
      return objectMapper.readTree(response.getBody()).at("/data/userId").asLong();
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to parse signup response: " + response.getBody(), e);
    }
  }

  /**
   * Calls {@code POST /auth/login} with {@code provider=LOCAL} and returns the access token from
   * {@code /data/accessToken}.
   */
  protected String loginUser(String email, String password) {
    Map<String, Object> body = Map.of("provider", "LOCAL", "email", email, "password", password);
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/auth/login",
            HttpMethod.POST,
            new HttpEntity<>(body, jsonOnlyHeaders()),
            String.class);
    if (!response.getStatusCode().is2xxSuccessful()) {
      throw new IllegalStateException(
          "login failed: status=" + response.getStatusCode() + ", body=" + response.getBody());
    }
    try {
      return objectMapper.readTree(response.getBody()).at("/data/accessToken").asText();
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to parse login response: " + response.getBody(), e);
    }
  }

  /**
   * Signup + login in one call with a generated unique email and the {@link
   * #DEFAULT_TEST_PASSWORD}. Convenience for tests that just need "some authenticated user" — tests
   * that need specific credentials should call {@link #signupUser} / {@link #loginUser} directly.
   */
  protected TestUser signupAndLogin(String nickname) {
    return signupAndLogin(randomEmail(), DEFAULT_TEST_PASSWORD, nickname);
  }

  /** Signup + login with the given credentials. Returns all four fields for downstream use. */
  protected TestUser signupAndLogin(String email, String password, String nickname) {
    Long userId = signupUser(email, password, nickname);
    String accessToken = loginUser(email, password);
    return new TestUser(userId, email, password, accessToken);
  }
}
