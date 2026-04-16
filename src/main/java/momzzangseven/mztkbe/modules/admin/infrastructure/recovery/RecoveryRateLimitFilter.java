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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
public class RecoveryRateLimitFilter extends OncePerRequestFilter {

  private static final String RECOVERY_PATH = "/admin/recovery/reseed";
  private static final int TOKENS_PER_MINUTE = 3;

  private final ObjectMapper objectMapper;
  private final boolean trustForwardedFor;
  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

  public RecoveryRateLimitFilter(
      ObjectMapper objectMapper,
      @Value("${mztk.admin.recovery.rate-limit.trust-forwarded-for:true}")
          boolean trustForwardedFor) {
    this.objectMapper = objectMapper;
    this.trustForwardedFor = trustForwardedFor;
  }

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

  /**
   * Extracts the client IP address from the request.
   *
   * <p>This service sits behind an AWS ALB whose security group is the sole inbound source for EC2
   * port 8080. The ALB appends the direct client IP as the <strong>last</strong> entry in {@code
   * X-Forwarded-For}, so the rightmost value is always trustworthy and cannot be spoofed.
   *
   * <p>The {@code mztk.admin.recovery.rate-limit.trust-forwarded-for} property defaults to {@code
   * true}. Set it to {@code false} only in environments where there is no reverse proxy (e.g. local
   * development without ALB).
   */
  private String extractIp(HttpServletRequest request) {
    if (trustForwardedFor) {
      String forwarded = request.getHeader("X-Forwarded-For");
      if (forwarded != null && !forwarded.isBlank()) {
        int comma = forwarded.lastIndexOf(',');
        return (comma >= 0 ? forwarded.substring(comma + 1) : forwarded).trim();
      }
    }
    return request.getRemoteAddr();
  }
}
