package momzzangseven.mztkbe.modules.web3.execution.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.SponsorPolicyResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetSponsorPolicyUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadSponsorPolicyPort;

@RequiredArgsConstructor
public class GetSponsorPolicyService implements GetSponsorPolicyUseCase {

  private final LoadSponsorPolicyPort loadSponsorPolicyPort;

  @Override
  public SponsorPolicyResult execute() {
    return SponsorPolicyResult.from(loadSponsorPolicyPort.loadSponsorPolicy());
  }
}
