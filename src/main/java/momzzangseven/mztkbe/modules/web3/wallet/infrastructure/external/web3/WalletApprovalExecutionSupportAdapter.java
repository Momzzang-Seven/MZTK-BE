package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.external.web3;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.PrepareEip7702AuthorizationCommand;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.PrepareEip7702AuthorizationResult;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.in.PrepareEip7702AuthorizationUseCase;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnUserExecutionEnabled;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionSupport;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalExecutionSupportPort;
import momzzangseven.mztkbe.modules.web3.wallet.infrastructure.config.WalletApprovalProperties;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnUserExecutionEnabled
public class WalletApprovalExecutionSupportAdapter
    implements LoadWalletApprovalExecutionSupportPort {

  private final PrepareEip7702AuthorizationUseCase prepareEip7702AuthorizationUseCase;
  private final WalletApprovalProperties approvalProperties;

  @Override
  public WalletApprovalExecutionSupport load(String authorityAddress) {
    String normalizedAuthority = EvmAddress.of(authorityAddress).value();
    String delegateTarget =
        EvmAddress.of(approvalProperties.getDelegationBatchImplAddress()).value();
    PrepareEip7702AuthorizationResult authorization =
        prepareEip7702AuthorizationUseCase.execute(
            new PrepareEip7702AuthorizationCommand(
                approvalProperties.getChainId(), delegateTarget, normalizedAuthority));
    return new WalletApprovalExecutionSupport(
        approvalProperties.getChainId(),
        delegateTarget,
        authorization.authorityNonce(),
        authorization.authorizationPayloadHash(),
        approvalProperties.getTtlSeconds());
  }
}
