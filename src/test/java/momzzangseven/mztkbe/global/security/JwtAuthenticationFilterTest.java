package momzzangseven.mztkbe.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import momzzangseven.mztkbe.modules.account.application.port.in.CheckAccountStatusUseCase;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter 단위 테스트")
class JwtAuthenticationFilterTest {

  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private CheckAccountStatusUseCase checkAccountStatusUseCase;
  @Mock private ObjectMapper objectMapper;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private FilterChain filterChain;

  @InjectMocks private JwtAuthenticationFilter filter;

  @BeforeEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // shouldNotFilter()
  // ─────────────────────────────────────────────────────────────────────────
  @Nested
  @DisplayName("shouldNotFilter() - 공개 경로 스킵")
  class ShouldNotFilter {

    @Test
    @DisplayName("/auth/login 경로는 필터 건너뜀")
    void loginPath_shouldBeSkipped() {
      when(request.getRequestURI()).thenReturn("/auth/login");
      assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    @DisplayName("/auth/signup 경로는 필터 건너뜀")
    void signupPath_shouldBeSkipped() {
      when(request.getRequestURI()).thenReturn("/auth/signup");
      assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    @DisplayName("/auth/reactivate 경로는 필터 건너뜀")
    void reactivatePath_shouldBeSkipped() {
      when(request.getRequestURI()).thenReturn("/auth/reactivate");
      assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    @DisplayName("그 외 경로는 필터 실행")
    void otherPath_shouldNotBeSkipped() {
      when(request.getRequestURI()).thenReturn("/users/me/level");
      assertThat(filter.shouldNotFilter(request)).isFalse();
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // doFilterInternal()
  // ─────────────────────────────────────────────────────────────────────────
  @Nested
  @DisplayName("doFilterInternal() - 토큰 처리 분기")
  class DoFilterInternal {

    @Test
    @DisplayName("Authorization 헤더 없으면 인증 설정 없이 체인 통과")
    void noAuthorizationHeader_passesThroughWithoutAuthentication() throws Exception {
      when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);

      filter.doFilterInternal(request, response, filterChain);

      verify(filterChain).doFilter(request, response);
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Bearer 접두사 없는 헤더이면 토큰 추출 실패로 체인 통과")
    void nonBearerHeader_passesThroughWithoutAuthentication() throws Exception {
      when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Basic abc");

      filter.doFilterInternal(request, response, filterChain);

      verify(filterChain).doFilter(request, response);
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("이미 인증 컨텍스트가 있으면 토큰 검증 없이 체인 통과")
    void alreadyAuthenticated_skipTokenValidation() throws Exception {
      when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer valid-token");
      SecurityContextHolder.getContext()
          .setAuthentication(
              new org.springframework.security.authentication.TestingAuthenticationToken(
                  "user", "pw", "ROLE_USER"));

      filter.doFilterInternal(request, response, filterChain);

      verify(jwtTokenProvider, never()).validateToken(anyString());
      verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("토큰 검증 실패(invalid)이면 체인 통과 (인증 미설정)")
    void invalidToken_passesThroughWithoutAuthentication() throws Exception {
      when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer bad-token");
      when(jwtTokenProvider.validateToken("bad-token")).thenReturn(false);

      filter.doFilterInternal(request, response, filterChain);

      verify(filterChain).doFilter(request, response);
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("validateToken=true이지만 액세스 토큰이 아니면 체인 통과")
    void validTokenButNotAccessToken_passesThroughWithoutAuthentication() throws Exception {
      when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer refresh-token");
      when(jwtTokenProvider.validateToken("refresh-token")).thenReturn(true);
      when(jwtTokenProvider.isAccessToken("refresh-token")).thenReturn(false);

      filter.doFilterInternal(request, response, filterChain);

      verify(filterChain).doFilter(request, response);
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("정상 액세스 토큰 & 활성 유저 → 인증 컨텍스트 설정 후 체인 통과")
    void validAccessTokenAndActiveUser_setsAuthentication() throws Exception {
      when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer access-token");
      when(jwtTokenProvider.validateToken("access-token")).thenReturn(true);
      when(jwtTokenProvider.isAccessToken("access-token")).thenReturn(true);
      when(jwtTokenProvider.getUserIdFromToken("access-token")).thenReturn(1L);
      when(jwtTokenProvider.getRoleFromToken("access-token")).thenReturn(UserRole.USER);
      when(jwtTokenProvider.isStepUpAccessToken("access-token")).thenReturn(false);
      when(checkAccountStatusUseCase.isActive(1L)).thenReturn(true);

      filter.doFilterInternal(request, response, filterChain);

      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
      assertThat(
              SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                  .anyMatch(a -> a.getAuthority().equals("ROLE_USER")))
          .isTrue();
      verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("step-up 액세스 토큰 → ROLE_STEP_UP 포함한 인증 컨텍스트 설정")
    void stepUpToken_setsStepUpAuthority() throws Exception {
      when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer stepup-token");
      when(jwtTokenProvider.validateToken("stepup-token")).thenReturn(true);
      when(jwtTokenProvider.isAccessToken("stepup-token")).thenReturn(true);
      when(jwtTokenProvider.getUserIdFromToken("stepup-token")).thenReturn(2L);
      when(jwtTokenProvider.getRoleFromToken("stepup-token")).thenReturn(UserRole.USER);
      when(jwtTokenProvider.isStepUpAccessToken("stepup-token")).thenReturn(true);
      when(checkAccountStatusUseCase.isActive(2L)).thenReturn(true);

      filter.doFilterInternal(request, response, filterChain);

      assertThat(
              SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                  .anyMatch(a -> a.getAuthority().equals("ROLE_STEP_UP")))
          .isTrue();
      verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("탈퇴한 유저(withdrawn)이면 409 에러 응답 후 체인 중단")
    void withdrawnUser_writesErrorResponseAndStopsChain() throws Exception {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);

      when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer withdrawn-token");
      when(jwtTokenProvider.validateToken("withdrawn-token")).thenReturn(true);
      when(jwtTokenProvider.isAccessToken("withdrawn-token")).thenReturn(true);
      when(jwtTokenProvider.getUserIdFromToken("withdrawn-token")).thenReturn(3L);
      when(jwtTokenProvider.getRoleFromToken("withdrawn-token")).thenReturn(UserRole.USER);
      when(jwtTokenProvider.isStepUpAccessToken("withdrawn-token")).thenReturn(false);
      when(checkAccountStatusUseCase.isActive(3L)).thenReturn(false);
      when(checkAccountStatusUseCase.isDeleted(3L)).thenReturn(true);
      when(response.getWriter()).thenReturn(pw);

      filter.doFilterInternal(request, response, filterChain);

      verify(filterChain, never()).doFilter(any(), any());
      verify(response).setStatus(409);
    }

    @Test
    @DisplayName("비활성 유저이지만 탈퇴 아닌 경우(인증 없이 체인 통과)")
    void inactiveButNotWithdrawnUser_passesThroughWithoutAuthentication() throws Exception {
      when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer inactive-token");
      when(jwtTokenProvider.validateToken("inactive-token")).thenReturn(true);
      when(jwtTokenProvider.isAccessToken("inactive-token")).thenReturn(true);
      when(jwtTokenProvider.getUserIdFromToken("inactive-token")).thenReturn(4L);
      when(jwtTokenProvider.getRoleFromToken("inactive-token")).thenReturn(UserRole.USER);
      when(jwtTokenProvider.isStepUpAccessToken("inactive-token")).thenReturn(false);
      when(checkAccountStatusUseCase.isActive(4L)).thenReturn(false);
      when(checkAccountStatusUseCase.isDeleted(4L)).thenReturn(false);

      filter.doFilterInternal(request, response, filterChain);

      verify(filterChain).doFilter(request, response);
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
  }
}
