package momzzangseven.mztkbe.modules.admin.infrastructure.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("RecoveryRateLimitFilter 단위 테스트")
class RecoveryRateLimitFilterTest {

  private RecoveryRateLimitFilter filter;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    filter = new RecoveryRateLimitFilter(objectMapper, false);
  }

  private MockHttpServletRequest postRecoveryRequest(String remoteAddr) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/recovery/reseed");
    request.setRemoteAddr(remoteAddr);
    return request;
  }

  @Nested
  @DisplayName("shouldNotFilter 검증")
  class ShouldNotFilterCases {

    @Test
    @DisplayName(
        "[M-182] shouldNotFilter returns true for GET /admin/recovery/reseed (wrong method)")
    void shouldNotFilter_getMethod_returnsTrue() {
      // given
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/recovery/reseed");

      // when
      boolean result = filter.shouldNotFilter(request);

      // then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("[M-183] shouldNotFilter returns true for POST /admin/accounts (wrong path)")
    void shouldNotFilter_wrongPath_returnsTrue() {
      // given
      MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/accounts");

      // when
      boolean result = filter.shouldNotFilter(request);

      // then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName(
        "[M-184] shouldNotFilter returns false for POST /admin/recovery/reseed (exact match)")
    void shouldNotFilter_exactMatch_returnsFalse() {
      // given
      MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/recovery/reseed");

      // when
      boolean result = filter.shouldNotFilter(request);

      // then
      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("doFilterInternal 레이트 리밋 검증")
  class DoFilterInternalCases {

    @Test
    @DisplayName("[M-185] doFilterInternal allows first 3 requests within one minute")
    void doFilterInternal_first3Requests_allPass() throws Exception {
      // given
      FilterChain filterChain = mock(FilterChain.class);

      // when & then
      for (int i = 0; i < 3; i++) {
        MockHttpServletRequest request = postRecoveryRequest("192.168.1.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, filterChain);
        assertThat(response.getStatus()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
      }

      verify(filterChain, times(3))
          .doFilter(
              org.mockito.ArgumentMatchers.any(MockHttpServletRequest.class),
              org.mockito.ArgumentMatchers.any(MockHttpServletResponse.class));
    }

    @Test
    @DisplayName(
        "[M-186] doFilterInternal returns 429 on 4th request from same IP within one minute")
    void doFilterInternal_4thRequest_returns429() throws Exception {
      // given
      FilterChain filterChain = mock(FilterChain.class);

      // when — exhaust 3 tokens
      for (int i = 0; i < 3; i++) {
        MockHttpServletRequest request = postRecoveryRequest("192.168.1.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, filterChain);
      }

      // 4th request
      MockHttpServletRequest request = postRecoveryRequest("192.168.1.1");
      MockHttpServletResponse response = new MockHttpServletResponse();
      filter.doFilterInternal(request, response, filterChain);

      // then
      assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
      verify(filterChain, times(3))
          .doFilter(
              org.mockito.ArgumentMatchers.any(MockHttpServletRequest.class),
              org.mockito.ArgumentMatchers.any(MockHttpServletResponse.class));
    }

    @Test
    @DisplayName("[M-187] doFilterInternal tracks different IPs independently")
    void doFilterInternal_differentIps_independentBuckets() throws Exception {
      // given
      FilterChain filterChain = mock(FilterChain.class);

      // when — exhaust bucket for IP 10.0.0.1
      for (int i = 0; i < 3; i++) {
        MockHttpServletRequest request = postRecoveryRequest("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, filterChain);
      }

      // request from different IP
      MockHttpServletRequest request = postRecoveryRequest("10.0.0.2");
      MockHttpServletResponse response = new MockHttpServletResponse();
      filter.doFilterInternal(request, response, filterChain);

      // then — second IP should pass
      assertThat(response.getStatus()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
      verify(filterChain, times(4))
          .doFilter(
              org.mockito.ArgumentMatchers.any(MockHttpServletRequest.class),
              org.mockito.ArgumentMatchers.any(MockHttpServletResponse.class));
    }

    @Test
    @DisplayName("[M-188] doFilterInternal uses X-Forwarded-For header when present")
    void doFilterInternal_xForwardedFor_usesFirstIp() throws Exception {
      // given
      FilterChain filterChain = mock(FilterChain.class);

      // when — exhaust bucket via X-Forwarded-For IP
      for (int i = 0; i < 3; i++) {
        MockHttpServletRequest request = postRecoveryRequest("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.50, 10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, filterChain);
      }

      // 4th request with same X-Forwarded-For
      MockHttpServletRequest request = postRecoveryRequest("10.0.0.1");
      request.addHeader("X-Forwarded-For", "203.0.113.50, 10.0.0.1");
      MockHttpServletResponse response = new MockHttpServletResponse();
      filter.doFilterInternal(request, response, filterChain);

      // then
      assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    @DisplayName("[M-189] doFilterInternal falls back to remoteAddr when X-Forwarded-For is absent")
    void doFilterInternal_noXForwardedFor_usesRemoteAddr() throws Exception {
      // given
      FilterChain filterChain = mock(FilterChain.class);

      // when — exhaust bucket via remoteAddr
      for (int i = 0; i < 3; i++) {
        MockHttpServletRequest request = postRecoveryRequest("192.168.1.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, filterChain);
      }

      // 4th request
      MockHttpServletRequest request = postRecoveryRequest("192.168.1.1");
      MockHttpServletResponse response = new MockHttpServletResponse();
      filter.doFilterInternal(request, response, filterChain);

      // then
      assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    @DisplayName(
        "[M-190] doFilterInternal response body on 429 is valid JSON with correct structure")
    void doFilterInternal_rateLimited_responseBodyIsValidJson() throws Exception {
      // given
      FilterChain filterChain = mock(FilterChain.class);

      // exhaust bucket
      for (int i = 0; i < 3; i++) {
        MockHttpServletRequest request = postRecoveryRequest("172.16.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, filterChain);
      }

      // 4th request
      MockHttpServletRequest request = postRecoveryRequest("172.16.0.1");
      MockHttpServletResponse response = new MockHttpServletResponse();
      filter.doFilterInternal(request, response, filterChain);

      // then
      assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);

      Map<String, Object> body =
          objectMapper.readValue(
              response.getContentAsString(), new TypeReference<Map<String, Object>>() {});
      assertThat(body).containsEntry("success", false);
      assertThat(body).containsEntry("message", "Too many requests");
      assertThat(body).containsEntry("code", "ADMIN_009");
    }
  }
}
