package momzzangseven.mztkbe.modules.web3.qna.application.service;

import java.util.ArrayList;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminExecutionAuthorityView;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminLocalAnswerView;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminLocalQuestionView;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminOnchainAnswerView;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminOnchainQuestionView;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminRefundReviewResult;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminReviewValidationItem;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminSettlementReviewResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAdminReviewContextPort.RefundContext;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAdminReviewContextPort.SettlementContext;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaQuestionState;

final class QnaAdminReviewDecider {

  static final String AUTHORITY_MODEL = "SERVER_RELAYER_ONLY";
  static final String LOCAL_QUESTION_MISSING = "LOCAL_QUESTION_MISSING";
  static final String LOCAL_QUESTION_NOT_QUESTION = "LOCAL_QUESTION_NOT_QUESTION";
  static final String LOCAL_QUESTION_NOT_OPEN = "LOCAL_QUESTION_NOT_OPEN";
  static final String LOCAL_ANSWER_MISSING = "LOCAL_ANSWER_MISSING";
  static final String LOCAL_ANSWER_POST_MISMATCH = "LOCAL_ANSWER_POST_MISMATCH";
  static final String LOCAL_ANSWER_ALREADY_ACCEPTED = "LOCAL_ANSWER_ALREADY_ACCEPTED";
  static final String ONCHAIN_QUESTION_MISSING = "ONCHAIN_QUESTION_MISSING";
  static final String ONCHAIN_QUESTION_NOT_REFUNDABLE = "ONCHAIN_QUESTION_NOT_REFUNDABLE";
  static final String ONCHAIN_ANSWER_MISSING = "ONCHAIN_ANSWER_MISSING";
  static final String ONCHAIN_ANSWER_QUESTION_MISMATCH = "ONCHAIN_ANSWER_QUESTION_MISMATCH";
  static final String ONCHAIN_ANSWER_ALREADY_ACCEPTED = "ONCHAIN_ANSWER_ALREADY_ACCEPTED";
  static final String QUESTION_HASH_MISMATCH = "QUESTION_HASH_MISMATCH";
  static final String ANSWER_HASH_MISMATCH = "ANSWER_HASH_MISMATCH";
  static final String ACTIVE_QUESTION_INTENT_PRESENT = "ACTIVE_QUESTION_INTENT_PRESENT";
  static final String ACTIVE_ANSWER_INTENT_PRESENT = "ACTIVE_ANSWER_INTENT_PRESENT";
  static final String RELAYER_NOT_REGISTERED = "RELAYER_NOT_REGISTERED";
  static final String INTERNAL_ISSUER_DISABLED = "INTERNAL_ISSUER_DISABLED";
  static final String INTERNAL_ISSUER_SETTLE_DISABLED = "INTERNAL_ISSUER_SETTLE_DISABLED";
  static final String INTERNAL_ISSUER_REFUND_DISABLED = "INTERNAL_ISSUER_REFUND_DISABLED";
  static final String ONCHAIN_QUESTION_HAS_ANSWERS = "ONCHAIN_QUESTION_HAS_ANSWERS";

  private QnaAdminReviewDecider() {}

  static QnaAdminSettlementReviewResult assessSettlement(
      Long postId, Long answerId, SettlementContext context) {
    if (postId == null || postId <= 0 || answerId == null || answerId <= 0) {
      throw new Web3InvalidInputException("postId/answerId must be positive");
    }

    var localQuestion = context.localQuestion().orElse(null);
    var localAnswer = context.localAnswer().orElse(null);
    var onchainQuestion = context.onchainQuestion().orElse(null);
    var onchainAnswer = context.onchainAnswer().orElse(null);
    boolean sameLocalQuestion =
        localQuestion != null && localAnswer != null && postId.equals(localAnswer.postId());
    boolean sameOnchainQuestion =
        onchainQuestion != null
            && onchainAnswer != null
            && onchainAnswer.getQuestionId().equals(onchainQuestion.getQuestionId());
    boolean questionHashMatches =
        localQuestion != null
            && onchainQuestion != null
            && localQuestion.content() != null
            && onchainQuestion
                .getQuestionHash()
                .equals(
                    momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaContentHashFactory.hash(
                        localQuestion.content()));
    boolean answerHashMatches =
        localAnswer != null
            && onchainAnswer != null
            && localAnswer.content() != null
            && onchainAnswer
                .getContentHash()
                .equals(
                    momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaContentHashFactory.hash(
                        localAnswer.content()));
    boolean questionConflict = context.activeQuestionIntent().isPresent();
    boolean answerConflict = context.activeAnswerIntent().isPresent();

    List<QnaAdminReviewValidationItem> validations = new ArrayList<>();
    validations.add(
        item(LOCAL_QUESTION_MISSING, localQuestion != null, false, "local question must exist"));
    validations.add(
        item(
            LOCAL_QUESTION_NOT_QUESTION,
            localQuestion != null && localQuestion.questionPost(),
            false,
            "post must be a question"));
    validations.add(
        item(
            LOCAL_QUESTION_NOT_OPEN,
            localQuestion != null && localQuestion.status() == PostStatus.OPEN,
            false,
            "local question must be OPEN"));
    validations.add(
        item(LOCAL_ANSWER_MISSING, localAnswer != null, false, "local answer must exist"));
    validations.add(
        item(
            LOCAL_ANSWER_POST_MISMATCH,
            sameLocalQuestion,
            false,
            "local answer must belong to the target question"));
    validations.add(
        item(
            LOCAL_ANSWER_ALREADY_ACCEPTED,
            localAnswer == null || !localAnswer.accepted(),
            false,
            "local answer must not already be accepted"));
    validations.add(
        item(
            ONCHAIN_QUESTION_MISSING,
            onchainQuestion != null,
            false,
            "onchain question projection must exist"));
    validations.add(
        item(
            ONCHAIN_QUESTION_NOT_REFUNDABLE,
            onchainQuestion != null && isSettlableState(onchainQuestion.getState()),
            false,
            "onchain question state must be CREATED or ANSWERED"));
    validations.add(
        item(
            ONCHAIN_ANSWER_MISSING,
            onchainAnswer != null,
            false,
            "onchain answer projection must exist"));
    validations.add(
        item(
            ONCHAIN_ANSWER_QUESTION_MISMATCH,
            sameOnchainQuestion,
            false,
            "onchain answer must belong to the target question"));
    validations.add(
        item(
            ONCHAIN_ANSWER_ALREADY_ACCEPTED,
            onchainAnswer == null || !onchainAnswer.isAccepted(),
            false,
            "onchain answer must not already be accepted"));
    validations.add(
        item(
            QUESTION_HASH_MISMATCH,
            questionHashMatches,
            false,
            "local question content must match onchain hash"));
    validations.add(
        item(
            ANSWER_HASH_MISMATCH,
            answerHashMatches,
            false,
            "local answer content must match onchain hash"));
    validations.add(
        item(
            ACTIVE_QUESTION_INTENT_PRESENT,
            context.activeQuestionIntent().isEmpty(),
            false,
            "question must not have an active execution intent"));
    validations.add(
        item(
            ACTIVE_ANSWER_INTENT_PRESENT,
            context.activeAnswerIntent().isEmpty(),
            false,
            "answer must not have an active execution intent"));
    validations.add(
        item(
            RELAYER_NOT_REGISTERED,
            context.authority().relayerRegistered(),
            false,
            "current server signer must be a registered relayer"));
    validations.add(
        item(
            INTERNAL_ISSUER_DISABLED,
            context.policy().enabled(),
            false,
            "internal issuer must be enabled"));
    validations.add(
        item(
            INTERNAL_ISSUER_SETTLE_DISABLED,
            context.policy().qnaAdminSettleEnabled(),
            false,
            "internal issuer must include QNA_ADMIN_SETTLE"));

    String blockingReason = firstBlockingReason(validations);
    return new QnaAdminSettlementReviewResult(
        postId,
        answerId,
        blockingReason == null,
        blockingReason,
        authorityView(context.authority()),
        new QnaAdminLocalQuestionView(
            localQuestion != null,
            localQuestion != null && localQuestion.questionPost(),
            localQuestion == null || localQuestion.status() == null
                ? null
                : localQuestion.status().name(),
            localQuestion != null && localQuestion.solved(),
            localQuestion != null && localQuestion.answerLocked(),
            localQuestion == null ? null : localQuestion.writerUserId(),
            localQuestion == null ? null : localQuestion.rewardMztk(),
            localQuestion == null ? null : localQuestion.acceptedAnswerId()),
        new QnaAdminLocalAnswerView(
            localAnswer != null,
            sameLocalQuestion,
            localAnswer != null && localAnswer.accepted(),
            localAnswer == null ? null : localAnswer.writerUserId()),
        new QnaAdminOnchainQuestionView(
            onchainQuestion != null,
            onchainQuestion == null ? null : onchainQuestion.getState().name(),
            onchainQuestion == null ? 0 : onchainQuestion.getAnswerCount()),
        new QnaAdminOnchainAnswerView(
            onchainAnswer != null,
            sameOnchainQuestion,
            onchainAnswer != null && onchainAnswer.isAccepted()),
        questionHashMatches,
        answerHashMatches,
        questionConflict,
        answerConflict,
        List.copyOf(validations));
  }

  static QnaAdminRefundReviewResult assessRefund(Long postId, RefundContext context) {
    if (postId == null || postId <= 0) {
      throw new Web3InvalidInputException("postId must be positive");
    }

    var localQuestion = context.localQuestion().orElse(null);
    var onchainQuestion = context.onchainQuestion().orElse(null);
    boolean questionConflict = context.activeQuestionIntent().isPresent();
    boolean answerConflict = !context.activeAnswerIntents().isEmpty();

    List<QnaAdminReviewValidationItem> validations = new ArrayList<>();
    validations.add(
        item(LOCAL_QUESTION_MISSING, localQuestion != null, false, "local question must exist"));
    validations.add(
        item(
            LOCAL_QUESTION_NOT_QUESTION,
            localQuestion != null && localQuestion.questionPost(),
            false,
            "post must be a question"));
    validations.add(
        item(
            LOCAL_QUESTION_NOT_OPEN,
            localQuestion != null && isRefundLocallyProcessableStatus(localQuestion.status()),
            false,
            "local question must be OPEN or PENDING_ADMIN_REFUND"));
    validations.add(
        item(
            ONCHAIN_QUESTION_MISSING,
            onchainQuestion != null,
            false,
            "onchain question projection must exist"));
    validations.add(
        item(
            ONCHAIN_QUESTION_NOT_REFUNDABLE,
            onchainQuestion != null && isRefundableState(onchainQuestion.getState()),
            false,
            "onchain question state must be CREATED or ANSWERED"));
    validations.add(
        item(
            ACTIVE_QUESTION_INTENT_PRESENT,
            context.activeQuestionIntent().isEmpty(),
            false,
            "question must not have an active execution intent"));
    validations.add(
        item(
            ACTIVE_ANSWER_INTENT_PRESENT,
            context.activeAnswerIntents().isEmpty(),
            false,
            "question answers must not have active execution intents"));
    validations.add(
        item(
            RELAYER_NOT_REGISTERED,
            context.authority().relayerRegistered(),
            false,
            "current server signer must be a registered relayer"));
    validations.add(
        item(
            INTERNAL_ISSUER_DISABLED,
            context.policy().enabled(),
            false,
            "internal issuer must be enabled"));
    validations.add(
        item(
            INTERNAL_ISSUER_REFUND_DISABLED,
            context.policy().qnaAdminRefundEnabled(),
            false,
            "internal issuer must include QNA_ADMIN_REFUND"));
    if (onchainQuestion != null && onchainQuestion.getAnswerCount() > 0) {
      validations.add(
          new QnaAdminReviewValidationItem(
              ONCHAIN_QUESTION_HAS_ANSWERS,
              true,
              true,
              "refund will move the question to DELETED_WITH_ANSWERS"));
    }

    String blockingReason = firstBlockingReason(validations);
    return new QnaAdminRefundReviewResult(
        postId,
        blockingReason == null,
        blockingReason,
        authorityView(context.authority()),
        new QnaAdminLocalQuestionView(
            localQuestion != null,
            localQuestion != null && localQuestion.questionPost(),
            localQuestion == null || localQuestion.status() == null
                ? null
                : localQuestion.status().name(),
            localQuestion != null && localQuestion.solved(),
            localQuestion != null && localQuestion.answerLocked(),
            localQuestion == null ? null : localQuestion.writerUserId(),
            localQuestion == null ? null : localQuestion.rewardMztk(),
            localQuestion == null ? null : localQuestion.acceptedAnswerId()),
        new QnaAdminOnchainQuestionView(
            onchainQuestion != null,
            onchainQuestion == null ? null : onchainQuestion.getState().name(),
            onchainQuestion == null ? 0 : onchainQuestion.getAnswerCount()),
        questionConflict,
        answerConflict,
        List.copyOf(validations));
  }

  static boolean isBadRequestCode(String code) {
    return LOCAL_QUESTION_MISSING.equals(code)
        || LOCAL_QUESTION_NOT_QUESTION.equals(code)
        || LOCAL_ANSWER_MISSING.equals(code)
        || LOCAL_ANSWER_POST_MISMATCH.equals(code);
  }

  private static QnaAdminReviewValidationItem item(
      String code, boolean valid, boolean warning, String detail) {
    return new QnaAdminReviewValidationItem(code, valid, warning, detail);
  }

  private static String firstBlockingReason(List<QnaAdminReviewValidationItem> validations) {
    return validations.stream()
        .filter(item -> !item.valid() && !item.warning())
        .map(QnaAdminReviewValidationItem::code)
        .findFirst()
        .orElse(null);
  }

  private static QnaAdminExecutionAuthorityView authorityView(
      momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAdminReviewContextPort
              .ExecutionAuthority
          authority) {
    return new QnaAdminExecutionAuthorityView(
        authority.signerAddress(), authority.relayerRegistered(), false, AUTHORITY_MODEL);
  }

  private static boolean isSettlableState(QnaQuestionState state) {
    return state == QnaQuestionState.CREATED || state == QnaQuestionState.ANSWERED;
  }

  private static boolean isRefundableState(QnaQuestionState state) {
    return isSettlableState(state);
  }

  private static boolean isRefundLocallyProcessableStatus(PostStatus status) {
    return status == PostStatus.OPEN || status == PostStatus.PENDING_ADMIN_REFUND;
  }
}
