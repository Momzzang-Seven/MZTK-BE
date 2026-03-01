package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RequestClientIpResolverAdapterTest {

  private final RequestClientIpResolverAdapter adapter = new RequestClientIpResolverAdapter();

  @AfterEach
  void tearDown() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void resolveClientIp_returnsUnknown_whenNoRequestContext() {
    RequestContextHolder.resetRequestAttributes();
    assertThat(adapter.resolveClientIp()).isEqualTo("unknown");
  }

  @Test
  void resolveClientIp_returnsUnknown_whenRemoteAddrMissing() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(" ");
    bind(request);

    assertThat(adapter.resolveClientIp()).isEqualTo("unknown");
  }

  @Test
  void resolveClientIp_returnsUnknown_whenRemoteAddrNull() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(null);
    bind(request);

    assertThat(adapter.resolveClientIp()).isEqualTo("unknown");
  }

  @Test
  void resolveClientIp_returnsRemoteAddr_whenProxyIsUntrusted() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("8.8.8.8");
    request.addHeader("X-Forwarded-For", "203.0.113.1");
    bind(request);

    assertThat(adapter.resolveClientIp()).isEqualTo("8.8.8.8");
  }

  @Test
  void resolveClientIp_returnsForwardedIp_whenLoopbackProxyAndForwardedIsValid() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.1");
    request.addHeader("X-Forwarded-For", "malicious-host, 203.0.113.9");
    bind(request);

    assertThat(adapter.resolveClientIp()).isEqualTo("203.0.113.9");
  }

  @Test
  void resolveClientIp_skipsEmptyForwardedCandidates() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.1");
    request.addHeader("X-Forwarded-For", " , , 203.0.113.9");
    bind(request);

    assertThat(adapter.resolveClientIp()).isEqualTo("203.0.113.9");
  }

  @Test
  void resolveClientIp_returnsRemoteAddr_whenTrustedProxyButForwardedInvalid() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("192.168.0.2");
    request.addHeader("X-Forwarded-For", "bad-host, another-host");
    bind(request);

    assertThat(adapter.resolveClientIp()).isEqualTo("192.168.0.2");
  }

  @Test
  void resolveClientIp_trustsIpv6UlaProxyAddress() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("fc00::1");
    request.addHeader("X-Forwarded-For", "198.51.100.22");
    bind(request);

    assertThat(adapter.resolveClientIp()).isEqualTo("198.51.100.22");
  }

  @Test
  void resolveClientIp_doesNotTrustPublicIpv6ProxyAddress() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("2001:db8::1");
    request.addHeader("X-Forwarded-For", "198.51.100.22");
    bind(request);

    assertThat(adapter.resolveClientIp()).isEqualTo("2001:db8::1");
  }

  @Test
  void resolveClientIp_returnsRemoteAddr_whenForwardedHeaderBlank() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.1");
    request.addHeader("X-Forwarded-For", " ");
    bind(request);

    assertThat(adapter.resolveClientIp()).isEqualTo("127.0.0.1");
  }

  @Test
  void resolveClientIp_returnsRemoteAddr_whenRemoteAddrNotIpLiteral() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("proxy.internal");
    request.addHeader("X-Forwarded-For", "203.0.113.9");
    bind(request);

    assertThat(adapter.resolveClientIp()).isEqualTo("proxy.internal");
  }

  private void bind(MockHttpServletRequest request) {
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }
}
