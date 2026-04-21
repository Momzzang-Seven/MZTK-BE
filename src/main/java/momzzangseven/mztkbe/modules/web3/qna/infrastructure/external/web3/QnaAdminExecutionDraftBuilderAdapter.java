package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.web3;

import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadInternalExecutionEip1559TtlPort;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionPayload;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraft;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaUnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.BuildQnaAdminExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAdminSignerAddressPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAdminSignerPendingNoncePort;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdCodec;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdempotencyKeyFactory;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.config.QnaEscrowProperties;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnQnaAdminOrAutoAcceptEnabled;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnQnaAdminOrAutoAcceptEnabled
public class QnaAdminExecutionDraftBuilderAdapter implements BuildQnaAdminExecutionDraftPort {

  private final LoadQnaAdminSignerAddressPort loadQnaAdminSignerAddressPort;
  private final LoadQnaAdminSignerPendingNoncePort loadQnaAdminSignerPendingNoncePort;
  private final LoadInternalExecutionEip1559TtlPort loadInternalExecutionEip1559TtlPort;
  private final Web3CoreProperties web3CoreProperties;
  private final QnaEscrowProperties qnaEscrowProperties;
  private final QnaEscrowAbiEncoder qnaEscrowAbiEncoder;
  private final QnaContractCallSupport qnaContractCallSupport;
  private final QnaPayloadSerializer qnaPayloadSerializer;
  private final QnaUnsignedTxFingerprintFactory qnaUnsignedTxFingerprintFactory;
  private final Clock appClock;

  @Override
  public QnaExecutionDraft build(QnaEscrowExecutionRequest request) {
    if (request.actionType() != QnaExecutionActionType.QNA_ADMIN_SETTLE
        && request.actionType() != QnaExecutionActionType.QNA_ADMIN_REFUND) {
      throw new IllegalStateException("admin draft builder supports only admin settle/refund");
    }

    String callTarget = EvmAddress.of(qnaEscrowProperties.getQnaContractAddress()).value();
    String questionId = QnaEscrowIdCodec.questionId(request.postId());
    String answerId =
        request.answerId() == null ? null : QnaEscrowIdCodec.answerId(request.answerId());
    String signerAddress = EvmAddress.of(loadQnaAdminSignerAddressPort.loadSignerAddress()).value();
    qnaContractCallSupport.requireRelayerCallable(callTarget, signerAddress);
    long expectedNonce = loadQnaAdminSignerPendingNoncePort.loadPendingNonce(signerAddress);

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
        qnaContractCallSupport.prevalidateContractCall(signerAddress, callTarget, callData);

    QnaUnsignedTxSnapshot unsignedTxSnapshot =
        new QnaUnsignedTxSnapshot(
            web3CoreProperties.getChainId(),
            signerAddress,
            callTarget,
            BigInteger.ZERO,
            callData,
            expectedNonce,
            prevalidation.gasLimit(),
            prevalidation.maxPriorityFeePerGas(),
            prevalidation.maxFeePerGas());

    QnaEscrowExecutionPayload payload =
        new QnaEscrowExecutionPayload(
            request.actionType(),
            request.postId(),
            request.answerId(),
            signerAddress,
            request.tokenAddress(),
            request.rewardAmountWei(),
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
        false,
        null,
        null,
        null,
        null,
        unsignedTxSnapshot,
        qnaUnsignedTxFingerprintFactory.compute(unsignedTxSnapshot),
        LocalDateTime.now(appClock)
            .plusSeconds(loadInternalExecutionEip1559TtlPort.loadTtlSeconds()));
  }
}
