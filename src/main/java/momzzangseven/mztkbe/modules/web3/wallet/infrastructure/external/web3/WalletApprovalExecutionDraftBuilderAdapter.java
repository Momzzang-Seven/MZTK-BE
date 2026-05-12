package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.external.web3;

import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.wallet.WalletApprovalUnavailableException;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnUserExecutionEnabled;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionDraft;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionPayload;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionRequest;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionSupport;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.BuildWalletApprovalExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalExecutionSupportPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.vo.WalletApprovalExecutionActionType;
import momzzangseven.mztkbe.modules.web3.wallet.domain.vo.WalletApprovalExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.wallet.domain.vo.WalletApprovalExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.wallet.domain.vo.WalletApprovalRootIdempotencyKeyFactory;
import momzzangseven.mztkbe.modules.web3.wallet.infrastructure.config.WalletApprovalProperties;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnUserExecutionEnabled
public class WalletApprovalExecutionDraftBuilderAdapter
    implements BuildWalletApprovalExecutionDraftPort {

  private final LoadWalletApprovalExecutionSupportPort loadWalletApprovalExecutionSupportPort;
  private final WalletApprovalProperties approvalProperties;
  private final WalletApprovalCalldataEncoder calldataEncoder;
  private final WalletApprovalPayloadSerializer payloadSerializer;
  private final Clock appClock;

  @Override
  public WalletApprovalExecutionDraft build(WalletApprovalExecutionRequest request) {
    if (!Boolean.TRUE.equals(approvalProperties.getEnabled())) {
      throw new WalletApprovalUnavailableException("wallet approval flow is disabled");
    }

    String tokenAddress = EvmAddress.of(approvalProperties.getTokenContractAddress()).value();
    String qnaEscrow = EvmAddress.of(approvalProperties.getQnaEscrowSpenderAddress()).value();
    String marketplaceEscrow =
        EvmAddress.of(approvalProperties.getMarketplaceEscrowSpenderAddress()).value();
    String authorityAddress = EvmAddress.of(request.walletAddress()).value();
    WalletApprovalExecutionSupport executionSupport =
        loadWalletApprovalExecutionSupportPort.load(authorityAddress);

    WalletApprovalExecutionDraftCall qnaCall = approveCall(tokenAddress, qnaEscrow);
    WalletApprovalExecutionDraftCall marketplaceCall = approveCall(tokenAddress, marketplaceEscrow);
    WalletApprovalExecutionPayload payload =
        new WalletApprovalExecutionPayload(
            WalletApprovalExecutionActionType.WALLET_ESCROW_APPROVE,
            request.registrationId(),
            request.requesterUserId(),
            authorityAddress,
            tokenAddress,
            List.of(
                toPayloadCall(qnaEscrow, qnaCall),
                toPayloadCall(marketplaceEscrow, marketplaceCall)));

    return new WalletApprovalExecutionDraft(
        WalletApprovalExecutionResourceType.WALLET_REGISTRATION,
        request.registrationId(),
        WalletApprovalExecutionResourceStatus.PENDING_EXECUTION,
        WalletApprovalExecutionActionType.WALLET_ESCROW_APPROVE,
        request.requesterUserId(),
        null,
        WalletApprovalRootIdempotencyKeyFactory.createForRegistration(request.registrationId()),
        payloadSerializer.hashHex(payload),
        payloadSerializer.serialize(payload),
        List.of(qnaCall, marketplaceCall),
        false,
        authorityAddress,
        executionSupport.authorityNonce(),
        executionSupport.delegateTarget(),
        executionSupport.authorizationPayloadHash(),
        null,
        null,
        LocalDateTime.now(appClock).plusSeconds(executionSupport.ttlSeconds()));
  }

  private WalletApprovalExecutionDraftCall approveCall(String tokenAddress, String spenderAddress) {
    return new WalletApprovalExecutionDraftCall(
        tokenAddress, BigInteger.ZERO, calldataEncoder.encodeApproveMax(spenderAddress));
  }

  private WalletApprovalExecutionPayload.ApprovalCall toPayloadCall(
      String spenderAddress, WalletApprovalExecutionDraftCall call) {
    return new WalletApprovalExecutionPayload.ApprovalCall(
        spenderAddress, WalletApprovalCalldataEncoder.MAX_UINT256, call.target(), call.data());
  }
}
