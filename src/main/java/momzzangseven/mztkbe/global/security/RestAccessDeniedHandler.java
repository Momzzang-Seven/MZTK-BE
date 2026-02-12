package momzzangseven.mztkbe.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.AppErrorCode;
import momzzangseven.mztkbe.global.error.auth.AuthErrorCode;
import momzzangseven.mztkbe.global.response.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

  private final ObjectMapper objectMapper;

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException, ServletException {

    String uri = request.getRequestURI();
    AppErrorCode errorCode = resolveErrorCode(request);

    log.debug(
        "Access denied: uri={}, code={}, message={}",
        uri,
        errorCode.getCode(),
        accessDeniedException.getMessage());

    writeErrorResponse(response, errorCode);
  }

  private AppErrorCode resolveErrorCode(HttpServletRequest request) {
    String uri = request.getRequestURI();
    String method = request.getMethod();

    if ("POST".equalsIgnoreCase(method) && "/users/me/withdrawal".equals(uri)) {
      return AuthErrorCode.STEP_UP_REQUIRED;
    }
    return AuthErrorCode.UNAUTHORIZED_ACCESS;
  }

  private void writeErrorResponse(HttpServletResponse response, AppErrorCode errorCode)
      throws IOException {
    response.setStatus(errorCode.getHttpStatus().value());
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
        response.getWriter(), ApiResponse.error(errorCode.getMessage(), errorCode.getCode()));
  }
}
