package momzzangseven.mztkbe.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.account.application.port.in.CheckAccountStatusUseCase;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT authentication filter.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Extract Bearer token from {@code Authorization} header
 *   <li>Validate access token and build authentication
 *   <li>Immediately block access tokens for non-ACTIVE users (soft-deleted users)
 * </ul>
 *
 * <p>Note: This filter is intentionally "fail-open" for missing/invalid tokens (it just doesn't set
 * authentication) so that Spring Security can handle authorization decisions consistently.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtTokenProvider jwtTokenProvider;
  private final CheckAccountStatusUseCase checkAccountStatusUseCase;
  private final ObjectMapper objectMapper;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    /*
     * NOTE:
     * - shouldNotFilter(...) returning true means "skip this JWT filter".
     * - /auth/login and /auth/signup are public endpoints; JWT auth is not required there.
     * - /auth/reactivate is also public and must be reachable even when the caller auto-attaches
     *   an old Authorization header that belongs to a DELETED user.
     *   This filter enforces "immediate block" for non-ACTIVE users by returning
     *   USER_WITHDRAWN(409) before the controller is invoked, so we skip it for reactivation.
     */
    return "/auth/login".equals(path)
        || "/auth/reactivate".equals(path)
        || "/auth/signup".equals(path);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String token = resolveBearerToken(request);
    if (token == null) {
      filterChain.doFilter(request, response);
      return;
    }

    if (SecurityContextHolder.getContext().getAuthentication() != null) {
      filterChain.doFilter(request, response);
      return;
    }

    if (!jwtTokenProvider.validateToken(token) || !jwtTokenProvider.isAccessToken(token)) {
      filterChain.doFilter(request, response);
      return;
    }

    Long userId = jwtTokenProvider.getUserIdFromToken(token);
    UserRole role = jwtTokenProvider.getRoleFromToken(token);
    boolean isStepUp = jwtTokenProvider.isStepUpAccessToken(token);

    if (!isActiveUser(userId)) {
      if (isWithdrawnUser(userId)) {
        writeErrorResponse(response, ErrorCode.USER_WITHDRAWN);
        return;
      }
      filterChain.doFilter(request, response);
      return;
    }

    setAuthentication(request, userId, role, isStepUp);

    filterChain.doFilter(request, response);
  }

  private String resolveBearerToken(HttpServletRequest request) {
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
      return null;
    }
    return authorization.substring(BEARER_PREFIX.length()).trim();
  }

  private boolean isActiveUser(Long userId) {
    return checkAccountStatusUseCase.isActive(userId);
  }

  private boolean isWithdrawnUser(Long userId) {
    return checkAccountStatusUseCase.isDeleted(userId);
  }

  private void writeErrorResponse(HttpServletResponse response, ErrorCode errorCode)
      throws IOException {
    response.setStatus(errorCode.getHttpStatus().value());
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
        response.getWriter(), ApiResponse.error(errorCode.getMessage(), errorCode.getCode()));
  }

  private void setAuthentication(
      HttpServletRequest request, Long userId, UserRole role, boolean isStepUp) {
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
            userId,
            null,
            isStepUp
                ? List.of(
                    new SimpleGrantedAuthority("ROLE_" + role.name()),
                    new SimpleGrantedAuthority("ROLE_STEP_UP"))
                : List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
    SecurityContextHolder.getContext().setAuthentication(authentication);
    log.debug("JWT authentication set: userId={}, role={}", userId, role);
  }
}
