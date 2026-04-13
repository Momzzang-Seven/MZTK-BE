package momzzangseven.mztkbe.modules.admin.application.port.out;

import java.util.List;
import momzzangseven.mztkbe.modules.admin.domain.vo.GeneratedAdminCredentials;

/** Output port for delivering generated admin credentials (plaintext) to operators. */
public interface BootstrapDeliveryPort {

  /** Deliver the plaintext credentials to the configured channel. */
  void deliver(List<GeneratedAdminCredentials> credentials);
}
