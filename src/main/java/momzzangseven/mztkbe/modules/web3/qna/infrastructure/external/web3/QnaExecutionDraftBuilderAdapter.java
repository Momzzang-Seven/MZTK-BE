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
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaServerSigPreimage;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaServerSigResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.SignQnaServerSigPort;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdCodec;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdempotencyKeyFactory;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.config.QnaEscrowProperties;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnUserExecutionEnabled;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.GetActiveWalletAddressUseCase;
import org.springframework.stereotype.Component;
import org.web3j.utils.Numeric;

@Component
@RequiredArgsConstructor
@ConditionalOnUserExecutionEnabled
public class QnaExecutionDraftBuilderAdapter implements BuildQnaExecutionDraftPort {

  private final GetActiveWalletAddressUseCase getActiveWalletAddressUseCase;
  private final Eip7702ChainPort eip7702ChainPort;
  private final Eip7702AuthorizationPort eip7702AuthorizationPort;
  private final Eip7702Properties eip7702Properties;
  private final Web3CoreProperties web3CoreProperties;
  private final QnaEscrowProperties qnaEscrowProperties;
  private final QnaEscrowAbiEncoder qnaEscrowAbiEncoder;
  private final QnaContractCallSupport qnaContractCallSupport;
  private final QnaPayloadSerializer qnaPayloadSerializer;
  private final QnaUnsignedTxFingerprintFactory qnaUnsignedTxFingerprintFactory;
  private final SignQnaServerSigPort signQnaServerSigPort;
  private final Clock appClock;

  @Override
  public QnaExecutionDraft build(QnaEscrowExecutionRequest request) {
    String callTarget = EvmAddress.of(qnaEscrowProperties.getQnaContractAddress()).value();
    String questionId = QnaEscrowIdCodec.questionId(request.postId());
    String answerId =
        request.answerId() == null ? null : QnaEscrowIdCodec.answerId(request.answerId());
    DraftContext draftContext = resolveDraftContext(request);

    // §5-2 ordering invariant: sign FIRST, encode SECOND, so that the exact same
    // (signedAt, signatureBytes) tuple flows into both the calldata and the payload
    // snapshot. KMS sign is invoked exactly once per build(...) call.
    QnaServerSigPreimage preimage =
        toPreimage(request.actionType(), draftContext.fromAddress(), questionId, answerId, request);
    QnaServerSigResult signResult = signQnaServerSigPort.sign(preimage);
    long signedAt = signResult.signedAt();
    byte[] signatureBytes = signResult.signatureBytes();

    String callData =
        qnaEscrowAbiEncoder.encode(
            request.actionType(),
            questionId,
            answerId,
            request.tokenAddress(),
            request.rewardAmountWei(),
            request.questionHash(),
            request.contentHash(),
            signedAt,
            signatureBytes);
    QnaExecutionDraftCall call = new QnaExecutionDraftCall(callTarget, BigInteger.ZERO, callData);
    QnaContractCallSupport.QnaCallPrevalidationResult prevalidation =
        qnaContractCallSupport.prevalidateContractCall( // 외부 RPC call
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
            callData,
            request.questionUpdateVersion(),
            request.questionUpdateToken(),
            request.answerUpdateVersion(),
            request.answerUpdateToken(),
            signedAt,
            Numeric.toHexString(signatureBytes));

    return new QnaExecutionDraft(
        request.resourceType(),
        request.resourceId(),
        QnaExecutionResourceStatus.PENDING_EXECUTION,
        request.actionType(),
        request.requesterUserId(),
        request.counterpartyUserId(),
        rootIdempotencyKey(request),
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
        signedAt,
        // §MOM-393 — derive expiresAt from the exact same Instant the sign call read, so
        // signedAt + sigValidityDuration and expiresAt cannot drift on sub-second clock reads.
        LocalDateTime.ofInstant(
            signResult.signingInstant().plusSeconds(draftContext.ttlSeconds()), appClock.getZone()));
  }

  private String rootIdempotencyKey(QnaEscrowExecutionRequest request) {
    if (request.actionType() == QnaExecutionActionType.QNA_QUESTION_UPDATE) {
      return QnaEscrowIdempotencyKeyFactory.createQuestionUpdate(
          request.requesterUserId(),
          request.postId(),
          request.questionUpdateVersion(),
          request.questionUpdateToken());
    }
    if (request.actionType() == QnaExecutionActionType.QNA_ANSWER_UPDATE) {
      return QnaEscrowIdempotencyKeyFactory.createAnswerUpdate(
          request.requesterUserId(),
          request.postId(),
          request.answerId(),
          request.answerUpdateVersion(),
          request.answerUpdateToken());
    }
    return QnaEscrowIdempotencyKeyFactory.create(
        request.actionType(), request.requesterUserId(), request.postId(), request.answerId());
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
      case QNA_QUESTION_CREATE, QNA_QUESTION_DELETE, QNA_ANSWER_ACCEPT -> rewardAmountWei;
      default -> BigInteger.ZERO;
    };
  }

  private DraftContext resolveDraftContext(QnaEscrowExecutionRequest request) {
    if (request.actionType() == QnaExecutionActionType.QNA_ADMIN_SETTLE
        || request.actionType() == QnaExecutionActionType.QNA_ADMIN_REFUND) {
      throw new IllegalStateException("user draft builder does not support admin settle/refund");
    }

    String authorityAddress = resolveActiveWalletAddress(request.requesterUserId());
    String delegateTarget =
        EvmAddress.of(eip7702Properties.getDelegation().getBatchImplAddress()).value();
    long authorityNonce =
        eip7702ChainPort
            .loadPendingAccountNonce(authorityAddress)
            .longValueExact(); // 이미 transaction 안에서 PESSIMISTIC LOCK 획득 후 외부 RPC call 존재.
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

  /**
   * Dispatch helper. Builds the {@link QnaServerSigPreimage} subtype that matches {@code
   * actionType}. The {@code actor} is always {@code draftContext.fromAddress()} since the user's
   * wallet is {@code msg.sender} for all 7 server-sig actions (asker for question/accept, responder
   * for answer flows).
   */
  private QnaServerSigPreimage toPreimage(
      QnaExecutionActionType actionType,
      String actor,
      String questionIdHex,
      String answerIdHex,
      QnaEscrowExecutionRequest request) {
    return switch (actionType) {
      case QNA_QUESTION_CREATE ->
          new QnaServerSigPreimage.CreateQuestionPreimage(
              actor,
              questionIdHex,
              request.tokenAddress(),
              request.rewardAmountWei(),
              request.questionHash());
      case QNA_QUESTION_UPDATE ->
          new QnaServerSigPreimage.UpdateQuestionPreimage(
              actor, questionIdHex, request.questionHash());
      case QNA_QUESTION_DELETE ->
          new QnaServerSigPreimage.DeleteQuestionPreimage(actor, questionIdHex);
      case QNA_ANSWER_SUBMIT ->
          new QnaServerSigPreimage.SubmitAnswerPreimage(
              actor, questionIdHex, answerIdHex, request.contentHash());
      case QNA_ANSWER_UPDATE ->
          new QnaServerSigPreimage.UpdateAnswerPreimage(
              actor, questionIdHex, answerIdHex, request.contentHash());
      case QNA_ANSWER_DELETE ->
          new QnaServerSigPreimage.DeleteAnswerPreimage(actor, questionIdHex, answerIdHex);
      case QNA_ANSWER_ACCEPT ->
          new QnaServerSigPreimage.AcceptAnswerPreimage(
              actor, questionIdHex, answerIdHex, request.questionHash(), request.contentHash());
      default ->
          throw new IllegalStateException(
              "server-sig draft builder does not support " + actionType);
    };
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
