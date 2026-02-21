package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter;

import jakarta.servlet.http.HttpServletRequest;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.ResolveClientIpPort;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class RequestClientIpResolverAdapter implements ResolveClientIpPort {

  @Override
  public String resolveClientIp() {
    ServletRequestAttributes attrs =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attrs == null) {
      return "unknown";
    }

    HttpServletRequest request = attrs.getRequest();
    String remoteAddr = request.getRemoteAddr();
    if (remoteAddr == null || remoteAddr.isBlank()) {
      return "unknown";
    }

    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (isTrustedProxy(remoteAddr) && forwardedFor != null && !forwardedFor.isBlank()) {
      String forwardedClientIp = extractFirstValidIp(forwardedFor);
      if (forwardedClientIp != null) {
        return forwardedClientIp;
      }
    }

    return remoteAddr;
  }

  private String extractFirstValidIp(String forwardedFor) {
    String[] candidates = forwardedFor.split(",");
    for (String candidate : candidates) {
      String trimmed = candidate == null ? "" : candidate.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      InetAddress parsed = parseIpLiteral(trimmed);
      if (parsed != null) {
        return parsed.getHostAddress();
      }
    }
    return null;
  }

  private boolean isTrustedProxy(String remoteAddr) {
    InetAddress parsed = parseIpLiteral(remoteAddr);
    if (parsed == null) {
      return false;
    }
    if (parsed.isLoopbackAddress() || parsed.isSiteLocalAddress()) {
      return true;
    }
    if (parsed instanceof Inet6Address inet6Address) {
      byte[] bytes = inet6Address.getAddress();
      return bytes.length > 0 && (bytes[0] & (byte) 0xFE) == (byte) 0xFC;
    }
    return false;
  }

  private InetAddress parseIpLiteral(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    // Accept only literal IP characters to avoid DNS lookups for hostnames.
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      boolean allowed =
          (ch >= '0' && ch <= '9')
              || (ch >= 'a' && ch <= 'f')
              || (ch >= 'A' && ch <= 'F')
              || ch == '.'
              || ch == ':';
      if (!allowed) {
        return null;
      }
    }
    try {
      return InetAddress.getByName(value);
    } catch (UnknownHostException ignored) {
      return null;
    }
  }
}
