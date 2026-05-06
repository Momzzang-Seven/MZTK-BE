package momzzangseven.mztkbe.modules.web3.shared.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.ExecutionSignerCapabilityView;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.ProbeExecutionSignerCapabilityUseCase;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.ProbeExecutionSignerCapabilityPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;

@RequiredArgsConstructor
public class ProbeExecutionSignerCapabilityService
    implements ProbeExecutionSignerCapabilityUseCase {

  private final ProbeExecutionSignerCapabilityPort probeExecutionSignerCapabilityPort;

  @Override
  public ExecutionSignerCapabilityView execute() {
    return probeExecutionSignerCapabilityPort.probe(TreasuryRole.SPONSOR.toAlias());
  }
}
