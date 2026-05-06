package momzzangseven.mztkbe.modules.web3.shared.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.ExecutionSignerCapabilityView;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.ProbeExecutionSignerCapabilityUseCase;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.LoadExecutionSignerConfigPort;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.ProbeExecutionSignerCapabilityPort;

@RequiredArgsConstructor
public class ProbeExecutionSignerCapabilityService
    implements ProbeExecutionSignerCapabilityUseCase {

  private final LoadExecutionSignerConfigPort loadExecutionSignerConfigPort;
  private final ProbeExecutionSignerCapabilityPort probeExecutionSignerCapabilityPort;

  @Override
  public ExecutionSignerCapabilityView execute() {
    var signerConfig = loadExecutionSignerConfigPort.load();
    return probeExecutionSignerCapabilityPort.probe(signerConfig.walletAlias());
  }
}
