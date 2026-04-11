package momzzangseven.mztkbe.modules.admin.infrastructure.delivery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.admin.application.port.out.BootstrapDeliveryPort;
import momzzangseven.mztkbe.modules.admin.domain.vo.GeneratedAdminCredentials;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Local/test implementation that delivers admin credentials via WARN log output. Also captures the
 * last delivery for test assertions.
 */
@Slf4j
@Component
@Profile({"dev", "test", "e2e"})
public class LogBootstrapDeliveryAdapter implements BootstrapDeliveryPort {

  private final AtomicReference<List<GeneratedAdminCredentials>> lastDelivery =
      new AtomicReference<>(Collections.emptyList());

  @Override
  public void deliver(List<GeneratedAdminCredentials> credentials) {
    lastDelivery.set(new ArrayList<>(credentials));
    for (GeneratedAdminCredentials cred : credentials) {
      log.warn("=== BOOTSTRAP ADMIN === loginId={}, password={}", cred.loginId(), cred.plaintext());
    }
  }

  /** Get the last delivered credentials (for test assertions). */
  public List<GeneratedAdminCredentials> getLastDelivery() {
    return Collections.unmodifiableList(lastDelivery.get());
  }
}
