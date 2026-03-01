package momzzangseven.mztkbe.modules.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.auth.application.dto.LoginResult;
import momzzangseven.mztkbe.modules.auth.application.port.in.ReactivateUseCase;
import momzzangseven.mztkbe.modules.auth.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.auth.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import momzzangseven.mztkbe.modules.user.domain.model.UserStatus;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * AuthController 통합 테스트 (MockMVC + H2).
 *
 * <p>검증 대상:
 *
 * <ul>
 *   <li>HTTP 상태 코드 (200, 204, 400, 401, 409)
 *   <li>응답 JSON 구조 ($.data.accessToken, $.data.userId 등)
 *   <li>Set-Cookie 헤더 (HttpOnly, Max-Age 속성)
 *   <li>Spring Security 인가 규칙 (permitAll vs authenticated)
 *   <li>Bean Validation (@NotNull, @Email, @Pattern 등)
 * </ul>
 *
 * <p>외부 API는 MockBean으로 대체, 내부 서비스 및 H2 DB는 실제 동작.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("AuthController MockMVC + H2 통합 테스트")
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  // Kakao / Google 외부 API는 MockBean으로 대체 (실제 HTTP 호출 차단)
  @MockBean private KakaoAuthPort kakaoAuthPort;
  @MockBean private GoogleAuthPort googleAuthPort;
  @MockBean private ReactivateUseCase reactivateUseCase;

  // MarkTransactionSucceededService는 @ConditionalOnProperty(web3.reward-token.enabled=true)로
  // 테스트 환경(enabled=false)에서 생성되지 않지만, MarkTransactionSucceededAdapter가 이를 주입하려
  // 시도하므로 MockBean으로 컨텍스트 로딩 오류를 방지한다.
  @MockBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  private static final String EMAIL = "auth-ctrl-test@example.com";
  private static final String PASSWORD = "Test@1234!";
  private static final String NICKNAME = "테스트유저";

  // ============================================================
  // Helper Methods
  // ============================================================

  private ResultActions performSignup(String email, String password, String nickname)
      throws Exception {
    return mockMvc.perform(
        post("/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(
                    Map.of("email", email, "password", password, "nickname", nickname))));
  }

  private MvcResult performLogin(String email, String password) throws Exception {
    return mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("provider", "LOCAL", "email", email, "password", password))))
        .andReturn();
  }

  /**
   * Set-Cookie 헤더에서 refreshToken 값(쿠키 값만)을 추출한다.
   *
   * <p>Set-Cookie 예시: {@code refreshToken=eyJ...; Path=/auth; HttpOnly; Max-Age=604800}
   */
  private String extractRefreshTokenFromSetCookie(MvcResult result) {
    String setCookieHeader = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    assertThat(setCookieHeader).as("Set-Cookie 헤더가 존재해야 함").isNotNull().contains("refreshToken=");
    return setCookieHeader.split(";")[0].replace("refreshToken=", "").trim();
  }

  /** 응답 JSON의 $.data.accessToken 값을 추출한다. */
  private String extractAccessToken(MvcResult result) throws Exception {
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    return body.at("/data/accessToken").asText();
  }

  private LoginResult buildLoginResultForReactivate(String email) {
    User user =
        User.builder()
            .id(1L)
            .email(email)
            .nickname("reactivated-user")
            .authProvider(AuthProvider.LOCAL)
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    return LoginResult.of(
        "access-token-reactivate", "refresh-token-reactivate", 900000L, 604800000L, false, user);
  }

  // ============================================================
  // POST /auth/signup
  // ============================================================

  @Nested
  @DisplayName("POST /auth/signup")
  class SignupTest {

    @Test
    @DisplayName("정상 입력 → 200 OK, data.userId 반환")
    void signup_validInput_returns200WithUserId() throws Exception {
      performSignup(EMAIL, PASSWORD, NICKNAME)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.userId").isNumber());
    }

    @Test
    @DisplayName("이메일 형식 오류 → 400 Bad Request")
    void signup_invalidEmailFormat_returns400() throws Exception {
      performSignup("not-an-email", PASSWORD, NICKNAME).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비밀번호 미달 (특수문자 없음) → 400 Bad Request")
    void signup_passwordWithoutSpecialChar_returns400() throws Exception {
      performSignup(EMAIL, "Password123", NICKNAME).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비밀번호 8자 미만 → 400 Bad Request")
    void signup_passwordTooShort_returns400() throws Exception {
      performSignup(EMAIL, "Sh@1", NICKNAME).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("닉네임 빈 문자열 → 400 Bad Request")
    void signup_blankNickname_returns400() throws Exception {
      performSignup(EMAIL, PASSWORD, "").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("이메일 중복 → 409 Conflict")
    void signup_duplicateEmail_returns409() throws Exception {
      performSignup(EMAIL, PASSWORD, NICKNAME).andExpect(status().isOk());
      performSignup(EMAIL, PASSWORD, "다른닉네임").andExpect(status().isConflict());
    }
  }

  // ============================================================
  // POST /auth/login
  // ============================================================

  @Nested
  @DisplayName("POST /auth/login")
  class LoginTest {

    @Test
    @DisplayName("LOCAL 로그인 성공 → 200 OK, accessToken 반환, HttpOnly refreshToken 쿠키 설정")
    void login_validLocalCredentials_returns200WithTokenAndCookie() throws Exception {
      // Given
      performSignup(EMAIL, PASSWORD, NICKNAME);

      // When
      MvcResult result = performLogin(EMAIL, PASSWORD);

      // Then – HTTP 상태
      assertThat(result.getResponse().getStatus()).isEqualTo(200);

      // Then – 응답 JSON 구조
      JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
      assertThat(body.at("/status").asText()).isEqualTo("SUCCESS");
      assertThat(body.at("/data/accessToken").asText()).isNotBlank();
      assertThat(body.at("/data/grantType").asText()).isEqualTo("Bearer");
      assertThat(body.at("/data/userInfo/userId").asLong()).isPositive();

      // Then – Set-Cookie 헤더에 HttpOnly refreshToken 설정
      String setCookieHeader = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
      assertThat(setCookieHeader)
          .isNotNull()
          .contains("refreshToken=")
          .contains("HttpOnly")
          .contains("Path=/auth");
    }

    @Test
    @DisplayName("잘못된 비밀번호 → 401 Unauthorized")
    void login_wrongPassword_returns401() throws Exception {
      performSignup(EMAIL, PASSWORD, NICKNAME);

      mockMvc
          .perform(
              post("/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          Map.of("provider", "LOCAL", "email", EMAIL, "password", "Wrong@9999"))))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("존재하지 않는 이메일 → 401 Unauthorized")
    void login_unknownEmail_returns401() throws Exception {
      mockMvc
          .perform(
              post("/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          Map.of(
                              "provider", "LOCAL",
                              "email", "nobody@example.com",
                              "password", PASSWORD))))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("provider 필드 누락 → 400 Bad Request (@NotNull 검증)")
    void login_missingProvider_returns400() throws Exception {
      mockMvc
          .perform(
              post("/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          Map.of("email", EMAIL, "password", PASSWORD))))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("이메일 형식 오류 → 400 Bad Request (@Email 검증)")
    void login_invalidEmailFormat_returns400() throws Exception {
      mockMvc
          .perform(
              post("/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          Map.of(
                              "provider", "LOCAL",
                              "email", "invalid-email",
                              "password", PASSWORD))))
          .andExpect(status().isBadRequest());
    }
  }

  // ============================================================
  // POST /auth/reactivate
  // ============================================================

  @Nested
  @DisplayName("POST /auth/reactivate")
  class ReactivateTest {

    @Test
    @DisplayName("유효한 요청 → 200 OK, accessToken/refreshToken 쿠키 반환")
    void reactivate_validRequest_returns200WithTokens() throws Exception {
      given(reactivateUseCase.execute(any())).willReturn(buildLoginResultForReactivate(EMAIL));

      MvcResult result =
          mockMvc
              .perform(
                  post("/auth/reactivate")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          objectMapper.writeValueAsString(
                              Map.of(
                                  "provider", "LOCAL",
                                  "email", EMAIL,
                                  "password", PASSWORD))))
              .andReturn();

      assertThat(result.getResponse().getStatus()).isEqualTo(200);
      JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
      assertThat(body.at("/status").asText()).isEqualTo("SUCCESS");
      assertThat(body.at("/data/accessToken").asText()).isNotBlank();
      assertThat(body.at("/data/grantType").asText()).isEqualTo("Bearer");

      String setCookieHeader = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
      assertThat(setCookieHeader).isNotNull().contains("refreshToken=").contains("HttpOnly");
    }

    @Test
    @DisplayName("provider 누락 → 400 Bad Request")
    void reactivate_missingProvider_returns400() throws Exception {
      mockMvc
          .perform(
              post("/auth/reactivate")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          Map.of("email", EMAIL, "password", PASSWORD))))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("이메일 형식 오류 → 400 Bad Request")
    void reactivate_invalidEmail_returns400() throws Exception {
      mockMvc
          .perform(
              post("/auth/reactivate")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          Map.of(
                              "provider", "LOCAL",
                              "email", "invalid-email",
                              "password", PASSWORD))))
          .andExpect(status().isBadRequest());
    }
  }

  // ============================================================
  // POST /auth/reissue
  // ============================================================

  @Nested
  @DisplayName("POST /auth/reissue")
  class ReissueTest {

    @Test
    @DisplayName("유효한 refreshToken 쿠키 → 200 OK, 새 accessToken + 새 refreshToken 쿠키")
    void reissue_validCookie_returns200WithNewTokens() throws Exception {
      // Given – 회원가입 → 로그인
      performSignup(EMAIL, PASSWORD, NICKNAME);
      MvcResult loginResult = performLogin(EMAIL, PASSWORD);
      String refreshToken = extractRefreshTokenFromSetCookie(loginResult);

      // When
      MvcResult reissueResult =
          mockMvc
              .perform(post("/auth/reissue").cookie(new Cookie("refreshToken", refreshToken)))
              .andReturn();

      // Then
      assertThat(reissueResult.getResponse().getStatus()).isEqualTo(200);

      JsonNode body = objectMapper.readTree(reissueResult.getResponse().getContentAsString());
      assertThat(body.at("/data/accessToken").asText()).isNotBlank();
      assertThat(body.at("/data/grantType").asText()).isEqualTo("Bearer");

      // 새 refreshToken 쿠키가 응답에 포함돼야 함
      String newSetCookie = reissueResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
      assertThat(newSetCookie).isNotNull().contains("refreshToken=").contains("HttpOnly");
    }

    @Test
    @DisplayName("refreshToken 쿠키 없음 → 400 Bad Request (required cookie)")
    void reissue_noCookie_returns400() throws Exception {
      mockMvc.perform(post("/auth/reissue")).andExpect(status().isBadRequest());
    }
  }

  // ============================================================
  // POST /auth/logout
  // ============================================================

  @Nested
  @DisplayName("POST /auth/logout")
  class LogoutTest {

    @Test
    @DisplayName("유효한 refreshToken 쿠키 → 204 No Content, Max-Age=0 쿠키 반환")
    void logout_withCookie_returns204AndExpiresCookie() throws Exception {
      // Given
      performSignup(EMAIL, PASSWORD, NICKNAME);
      MvcResult loginResult = performLogin(EMAIL, PASSWORD);
      String refreshToken = extractRefreshTokenFromSetCookie(loginResult);

      // When
      MvcResult logoutResult =
          mockMvc
              .perform(post("/auth/logout").cookie(new Cookie("refreshToken", refreshToken)))
              .andReturn();

      // Then
      assertThat(logoutResult.getResponse().getStatus()).isEqualTo(204);

      String setCookieHeader = logoutResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
      assertThat(setCookieHeader)
          .as("로그아웃 응답은 Max-Age=0 쿠키를 설정해야 함")
          .isNotNull()
          .contains("Max-Age=0");
    }

    @Test
    @DisplayName("쿠키 없이 로그아웃 → 204 No Content (optional cookie, 정상 처리)")
    void logout_withoutCookie_returns204() throws Exception {
      mockMvc.perform(post("/auth/logout")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("빈 refreshToken 쿠키 → 204 No Content, 쿠키 만료 처리")
    void logout_blankCookie_returns204AndExpiresCookie() throws Exception {
      MvcResult logoutResult =
          mockMvc
              .perform(post("/auth/logout").cookie(new Cookie("refreshToken", "   ")))
              .andReturn();

      assertThat(logoutResult.getResponse().getStatus()).isEqualTo(204);
      String setCookieHeader = logoutResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
      assertThat(setCookieHeader).isNotNull().contains("Max-Age=0");
    }
  }

  // ============================================================
  // POST /auth/stepup
  // ============================================================

  @Nested
  @DisplayName("POST /auth/stepup")
  class StepUpTest {

    @Test
    @DisplayName("Bearer 토큰 없이 요청 → 401 Unauthorized (Spring Security 인가 거부)")
    void stepup_withoutBearerToken_returns401() throws Exception {
      mockMvc
          .perform(
              post("/auth/stepup")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(Map.of("password", PASSWORD))))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("유효한 Bearer 토큰 + 올바른 비밀번호 → 200 OK, step-up accessToken 반환")
    void stepup_validBearerTokenAndPassword_returns200WithStepUpToken() throws Exception {
      // Given – 회원가입 → 로그인
      performSignup(EMAIL, PASSWORD, NICKNAME);
      MvcResult loginResult = performLogin(EMAIL, PASSWORD);
      String accessToken = extractAccessToken(loginResult);

      // When
      MvcResult stepUpResult =
          mockMvc
              .perform(
                  post("/auth/stepup")
                      .header("Authorization", "Bearer " + accessToken)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(objectMapper.writeValueAsString(Map.of("password", PASSWORD))))
              .andReturn();

      // Then
      assertThat(stepUpResult.getResponse().getStatus()).isEqualTo(200);

      JsonNode body = objectMapper.readTree(stepUpResult.getResponse().getContentAsString());
      assertThat(body.at("/data/accessToken").asText()).isNotBlank();
      assertThat(body.at("/data/grantType").asText()).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("유효한 Bearer 토큰 + 잘못된 비밀번호 → 401 Unauthorized")
    void stepup_wrongPassword_returns401() throws Exception {
      // Given
      performSignup(EMAIL, PASSWORD, NICKNAME);
      MvcResult loginResult = performLogin(EMAIL, PASSWORD);
      String accessToken = extractAccessToken(loginResult);

      // When / Then
      mockMvc
          .perform(
              post("/auth/stepup")
                  .header("Authorization", "Bearer " + accessToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(Map.of("password", "Wrong@9999"))))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("인증 객체의 principal이 null이면 401 Unauthorized")
    void stepup_nullPrincipal_returns401() throws Exception {
      mockMvc
          .perform(
              post("/auth/stepup")
                  .with(
                      authentication(
                          new UsernamePasswordAuthenticationToken(
                              null, null, List.of(new SimpleGrantedAuthority("ROLE_USER")))))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(Map.of("password", PASSWORD))))
          .andExpect(status().isUnauthorized());
    }
  }
}
