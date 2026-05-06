package momzzangseven.mztkbe.modules.web3.shared.application.port.out;

import momzzangseven.mztkbe.modules.web3.shared.application.dto.ExecutionSignerCapabilityView;

public interface ProbeExecutionSignerCapabilityPort {

  ExecutionSignerCapabilityView probe(String walletAlias);
}
