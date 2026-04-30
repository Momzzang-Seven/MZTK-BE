package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminRelayerRegistrationStatus;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaAnswerProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaQuestionProjection;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.ExecutionSignerCapabilityView;

public interface LoadQnaAdminReviewContextPort {

  SettlementContext loadSettlement(Long postId, Long answerId);

  SettlementContext loadSettlementForUpdate(Long postId, Long answerId);

  RefundContext loadRefund(Long postId);

  RefundContext loadRefundForUpdate(Long postId);

  enum LocalQuestionStatus {
    OPEN,
    PENDING_ACCEPT,
    PENDING_ADMIN_REFUND,
    RESOLVED,
    UNKNOWN;

    public static LocalQuestionStatus fromExternalName(String name) {
      if (name == null || name.isBlank()) {
        return UNKNOWN;
      }
      try {
        return LocalQuestionStatus.valueOf(name);
      } catch (IllegalArgumentException ignored) {
        return UNKNOWN;
      }
    }
  }

  record ExecutionAuthority(
      ExecutionSignerCapabilityView serverSigner,
      boolean relayerRegistered,
      QnaAdminRelayerRegistrationStatus relayerRegistrationStatus) {}

  record LocalQuestion(
      Long postId,
      Long writerUserId,
      boolean questionPost,
      LocalQuestionStatus status,
      boolean solved,
      boolean answerLocked,
      String content,
      Long rewardMztk,
      Long acceptedAnswerId) {}

  record LocalAnswer(
      Long answerId, Long postId, Long writerUserId, String content, boolean accepted) {}

  record SettlementContext(
      Optional<LocalQuestion> localQuestion,
      Optional<LocalAnswer> localAnswer,
      Optional<QnaQuestionProjection> onchainQuestion,
      Optional<QnaAnswerProjection> onchainAnswer,
      Optional<QnaExecutionIntentStateView> activeQuestionIntent,
      Optional<QnaExecutionIntentStateView> activeAnswerIntent,
      ExecutionAuthority authority,
      LoadExecutionInternalIssuerPolicyPort.ExecutionInternalIssuerPolicy policy) {}

  record RefundContext(
      Optional<LocalQuestion> localQuestion,
      Optional<QnaQuestionProjection> onchainQuestion,
      Optional<QnaExecutionIntentStateView> activeQuestionIntent,
      List<QnaExecutionIntentStateView> activeAnswerIntents,
      ExecutionAuthority authority,
      LoadExecutionInternalIssuerPolicyPort.ExecutionInternalIssuerPolicy policy) {}
}
