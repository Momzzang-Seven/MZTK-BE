package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.web3;

import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.wallet.WalletNotConnectedException;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702AuthorizationPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702ChainPort;
import momzzangseven.mztkbe.modules.web3.eip7702.infrastructure.config.Eip7702Properties;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionPayload;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraft;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaUnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.BuildQnaExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAdminSignerAddressPort;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdCodec;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdempotencyKeyFactory;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.config.QnaEscrowProperties;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.GetActiveWalletAddressUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class QnaExecutionDraftBuilderAdapter implements BuildQnaExecutionDraftPort {

  private final GetActiveWalletAddressUseCase getActiveWalletAddressUseCase;
  private final LoadQnaAdminSignerAddressPort loadQnaAdminSignerAddressPort;
  private final Eip7702ChainPort eip7702ChainPort;
  private final Eip7702AuthorizationPort eip7702AuthorizationPort;
  private final Eip7702Properties eip7702Properties;
  private final Web3CoreProperties web3CoreProperties;
  private final QnaEscrowProperties qnaEscrowProperties;
  private final QnaEscrowAbiEncoder qnaEscrowAbiEncoder;
  private final QnaContractCallSupport qnaContractCallSupport;
  private final QnaPayloadSerializer qnaPayloadSerializer;
  private final QnaUnsignedTxFingerprintFactory qnaUnsignedTxFingerprintFactory;
  private final Clock appClock;

  @Override
  public QnaExecutionDraft build(QnaEscrowExecutionRequest request) {
    String callTarget = EvmAddress.of(qnaEscrowProperties.getQnaContractAddress()).value();
    String questionId = QnaEscrowIdCodec.questionId(request.postId());
    String answerId =
        request.answerId() == null ? null : QnaEscrowIdCodec.answerId(request.answerId());
    DraftContext draftContext = resolveDraftContext(request, callTarget);

    String callData =
        qnaEscrowAbiEncoder.encode(
            request.actionType(),
            questionId,
            answerId,
            request.tokenAddress(),
            request.rewardAmountWei(),
            request.questionHash(),
            request.contentHash());
    QnaExecutionDraftCall call = new QnaExecutionDraftCall(callTarget, BigInteger.ZERO, callData);
    QnaContractCallSupport.QnaCallPrevalidationResult prevalidation =
        qnaContractCallSupport.prevalidateContractCall(
            draftContext.fromAddress(), callTarget, callData);

    QnaUnsignedTxSnapshot unsignedTxSnapshot =
        new QnaUnsignedTxSnapshot(
            requestChainId(),
            draftContext.fromAddress(),
            callTarget,
            BigInteger.ZERO,
            callData,
            draftContext.expectedNonce(),
            prevalidation.gasLimit(),
            prevalidation.maxPriorityFeePerGas(),
            prevalidation.maxFeePerGas());

    QnaEscrowExecutionPayload payload =
        new QnaEscrowExecutionPayload(
            request.actionType(),
            request.postId(),
            request.answerId(),
            draftContext.fromAddress(),
            request.tokenAddress(),
            amountForAction(request.actionType(), request.rewardAmountWei()),
            request.questionHash(),
            request.contentHash(),
            callTarget,
            callData);

    return new QnaExecutionDraft(
        request.resourceType(),
        request.resourceId(),
        QnaExecutionResourceStatus.PENDING_EXECUTION,
        request.actionType(),
        request.requesterUserId(),
        request.counterpartyUserId(),
        QnaEscrowIdempotencyKeyFactory.create(
            request.actionType(), request.requesterUserId(), request.postId(), request.answerId()),
        qnaPayloadSerializer.hashHex(payload),
        qnaPayloadSerializer.serialize(payload),
        List.of(call),
        draftContext.fallbackAllowed(),
        draftContext.authorityAddress(),
        draftContext.authorityNonce(),
        draftContext.delegateTarget(),
        draftContext.authorizationPayloadHash(),
        unsignedTxSnapshot,
        qnaUnsignedTxFingerprintFactory.compute(unsignedTxSnapshot),
        LocalDateTime.now(appClock).plusSeconds(draftContext.ttlSeconds()));
  }

  private long requestChainId() {
    return web3CoreProperties.getChainId();
  }

  private String resolveActiveWalletAddress(Long requesterUserId) {
    return getActiveWalletAddressUseCase
        .execute(requesterUserId)
        .map(address -> EvmAddress.of(address).value())
        .orElseThrow(() -> new WalletNotConnectedException(requesterUserId));
  }

  private BigInteger amountForAction(
      QnaExecutionActionType actionType, BigInteger rewardAmountWei) {
    return switch (actionType) {
      case QNA_QUESTION_CREATE, QNA_QUESTION_DELETE, QNA_ANSWER_ACCEPT, QNA_ADMIN_SETTLE ->
          rewardAmountWei;
      default -> BigInteger.ZERO;
    };
  }

  private DraftContext resolveDraftContext(
      QnaEscrowExecutionRequest request, String contractAddress) {
    if (request.actionType() == QnaExecutionActionType.QNA_ADMIN_SETTLE) {
      String signerAddress =
          EvmAddress.of(loadQnaAdminSignerAddressPort.loadSignerAddress()).value();
      qnaContractCallSupport.requireAdminCallable(contractAddress, signerAddress);
      long expectedNonce = eip7702ChainPort.loadPendingAccountNonce(signerAddress).longValueExact();
      return new DraftContext(
          signerAddress,
          false,
          null,
          null,
          null,
          expectedNonce,
          eip7702Properties.getAuthorization().getEip1559TtlSeconds(),
          null);
    }

    String authorityAddress = resolveActiveWalletAddress(request.requesterUserId());
    String delegateTarget =
        EvmAddress.of(eip7702Properties.getDelegation().getBatchImplAddress()).value();
    long authorityNonce =
        eip7702ChainPort.loadPendingAccountNonce(authorityAddress).longValueExact();
    String authorizationPayloadHash =
        eip7702AuthorizationPort.buildSigningHashHex(
            requestChainId(), delegateTarget, BigInteger.valueOf(authorityNonce));
    return new DraftContext(
        authorityAddress,
        true,
        authorityAddress,
        authorityNonce,
        delegateTarget,
        authorityNonce,
        eip7702Properties.getAuthorization().getTtlSeconds(),
        authorizationPayloadHash);
  }

  private record DraftContext(
      String fromAddress,
      boolean fallbackAllowed,
      String authorityAddress,
      Long authorityNonce,
      String delegateTarget,
      long expectedNonce,
      int ttlSeconds,
      String authorizationPayloadHash) {}
}
