package momzzangseven.mztkbe.modules.user.application.port.out;

import momzzangseven.mztkbe.modules.user.domain.model.ExternalDisconnectTask;

/** Output port for persisting external disconnect tasks. */
public interface SaveExternalDisconnectTaskPort {
  ExternalDisconnectTask save(ExternalDisconnectTask task);
}
