package momzzangseven.mztkbe.modules.admin.infrastructure.recovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate limit filter for the recovery reseed endpoint. Limits each IP to 3 requests per minute using
 * Bucket4j.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecoveryRateLimitFilter extends OncePerRequestFilter {

  private static final String RECOVERY_PATH = "/admin/recovery/reseed";
  private static final int TOKENS_PER_MINUTE = 3;

  private final ObjectMapper objectMapper;
  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !("POST".equalsIgnoreCase(request.getMethod())
        && RECOVERY_PATH.equals(request.getRequestURI()));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String ip = extractIp(request);
    Bucket bucket = buckets.computeIfAbsent(ip, k -> createBucket());

    if (bucket.tryConsume(1)) {
      filterChain.doFilter(request, response);
    } else {
      log.warn("Recovery rate limit exceeded for IP: {}", ip);
      response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      Map<String, Object> body =
          Map.of(
              "success", false,
              "message", "Too many requests",
              "code", "ADMIN_009");
      objectMapper.writeValue(response.getWriter(), body);
    }
  }

  private Bucket createBucket() {
    return Bucket.builder()
        .addLimit(Bandwidth.simple(TOKENS_PER_MINUTE, Duration.ofMinutes(1)))
        .build();
  }

  private String extractIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
