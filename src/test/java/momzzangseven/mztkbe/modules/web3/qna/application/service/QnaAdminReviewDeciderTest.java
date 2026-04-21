package momzzangseven.mztkbe.modules.web3.qna.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadExecutionInternalIssuerPolicyPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAdminReviewContextPort.ExecutionAuthority;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAdminReviewContextPort.LocalAnswer;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAdminReviewContextPort.LocalQuestion;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAdminReviewContextPort.RefundContext;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAdminReviewContextPort.SettlementContext;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaExecutionIntentStateView;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaAnswerProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaQuestionProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaContentHashFactory;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdCodec;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("QnaAdminReviewDecider unit test")
class QnaAdminReviewDeciderTest {

  @Test
  @DisplayName("settlement review treats same-action active intents as conflicts")
  void assessSettlement_marksSameActionActiveIntentAsConflict() {
    SettlementContext context =
        new SettlementContext(
            Optional.of(localQuestion()),
            Optional.of(localAnswer()),
            Optional.of(onchainQuestion(1)),
            Optional.of(onchainAnswer()),
            Optional.of(
                activeIntent("intent-question-active", QnaExecutionActionType.QNA_ADMIN_SETTLE)),
            Optional.of(
                activeIntent("intent-answer-active", QnaExecutionActionType.QNA_ADMIN_SETTLE)),
            authority(),
            enabledPolicy());

    var review = QnaAdminReviewDecider.assessSettlement(101L, 201L, context);

    assertThat(review.processable()).isFalse();
    assertThat(review.blockingReason())
        .isEqualTo(QnaAdminReviewDecider.ACTIVE_QUESTION_INTENT_PRESENT);
    assertThat(review.questionConflictingActiveIntent()).isTrue();
    assertThat(review.answerConflictingActiveIntent()).isTrue();
    assertThat(review.validations())
        .filteredOn(
            item -> QnaAdminReviewDecider.ACTIVE_QUESTION_INTENT_PRESENT.equals(item.code()))
        .singleElement()
        .satisfies(item -> assertThat(item.valid()).isFalse());
    assertThat(review.validations())
        .filteredOn(item -> QnaAdminReviewDecider.ACTIVE_ANSWER_INTENT_PRESENT.equals(item.code()))
        .singleElement()
        .satisfies(item -> assertThat(item.valid()).isFalse());
  }

  @Test
  @DisplayName("refund review blocks when an answer on the question has an active execution intent")
  void assessRefund_blocksWhenAnswerIntentExists() {
    RefundContext context =
        new RefundContext(
            Optional.of(localQuestion()),
            Optional.of(onchainQuestion(1)),
            Optional.empty(),
            List.of(activeIntent("intent-answer-active", QnaExecutionActionType.QNA_ANSWER_ACCEPT)),
            authority(),
            enabledPolicy());

    var review = QnaAdminReviewDecider.assessRefund(101L, context);

    assertThat(review.processable()).isFalse();
    assertThat(review.blockingReason())
        .isEqualTo(QnaAdminReviewDecider.ACTIVE_ANSWER_INTENT_PRESENT);
    assertThat(review.answerConflictingActiveIntent()).isTrue();
    assertThat(review.validations())
        .filteredOn(item -> QnaAdminReviewDecider.ACTIVE_ANSWER_INTENT_PRESENT.equals(item.code()))
        .singleElement()
        .satisfies(item -> assertThat(item.valid()).isFalse());
  }

  @Test
  @DisplayName("refund review treats same-action question intent as a conflict")
  void assessRefund_marksSameActionQuestionIntentAsConflict() {
    RefundContext context =
        new RefundContext(
            Optional.of(localQuestion()),
            Optional.of(onchainQuestion(0)),
            Optional.of(
                activeIntent("intent-question-active", QnaExecutionActionType.QNA_ADMIN_REFUND)),
            List.of(),
            authority(),
            enabledPolicy());

    var review = QnaAdminReviewDecider.assessRefund(101L, context);

    assertThat(review.processable()).isFalse();
    assertThat(review.blockingReason())
        .isEqualTo(QnaAdminReviewDecider.ACTIVE_QUESTION_INTENT_PRESENT);
    assertThat(review.questionConflictingActiveIntent()).isTrue();
  }

  @Test
  @DisplayName("refund review allows retry when local question is already pending admin refund")
  void assessRefund_allowsPendingAdminRefundStatus() {
    RefundContext context =
        new RefundContext(
            Optional.of(localQuestion(PostStatus.PENDING_ADMIN_REFUND)),
            Optional.of(onchainQuestion(0)),
            Optional.empty(),
            List.of(),
            authority(),
            enabledPolicy());

    var review = QnaAdminReviewDecider.assessRefund(101L, context);

    assertThat(review.processable()).isTrue();
    assertThat(review.blockingReason()).isNull();
  }

  private LocalQuestion localQuestion() {
    return localQuestion(PostStatus.OPEN);
  }

  private LocalQuestion localQuestion(PostStatus status) {
    boolean pendingAdminRefund = status == PostStatus.PENDING_ADMIN_REFUND;
    return new LocalQuestion(
        101L, 7L, true, status, pendingAdminRefund, pendingAdminRefund, "질문 본문", 50L, null);
  }

  private LocalAnswer localAnswer() {
    return new LocalAnswer(201L, 101L, 22L, "답변 본문", false);
  }

  private QnaQuestionProjection onchainQuestion(int answerCount) {
    return QnaQuestionProjection.create(
            101L,
            7L,
            QnaEscrowIdCodec.questionId(101L),
            "0x1111111111111111111111111111111111111111",
            new BigInteger("50000000000000000000"),
            QnaContentHashFactory.hash("질문 본문"))
        .syncAnswerCount(answerCount);
  }

  private QnaAnswerProjection onchainAnswer() {
    return QnaAnswerProjection.create(
        201L,
        101L,
        QnaEscrowIdCodec.questionId(101L),
        QnaEscrowIdCodec.answerId(201L),
        22L,
        QnaContentHashFactory.hash("답변 본문"));
  }

  private QnaExecutionIntentStateView activeIntent(String id, QnaExecutionActionType actionType) {
    return new QnaExecutionIntentStateView(
        id, actionType, ExecutionIntentStatus.AWAITING_SIGNATURE);
  }

  private ExecutionAuthority authority() {
    return new ExecutionAuthority("0x2222222222222222222222222222222222222222", true);
  }

  private LoadExecutionInternalIssuerPolicyPort.ExecutionInternalIssuerPolicy enabledPolicy() {
    return new LoadExecutionInternalIssuerPolicyPort.ExecutionInternalIssuerPolicy(
        true, true, true);
  }
}
