package momzzangseven.mztkbe.modules.web3.qna.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.ExecuteQnaAdminRefundCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAdminRefundCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.PrepareQnaInternalRefundUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadExecutionInternalIssuerPolicyPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAdminReviewContextPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAdminReviewContextPort.ExecutionAuthority;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAdminReviewContextPort.LocalQuestion;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAdminReviewContextPort.LocalQuestionStatus;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAdminReviewContextPort.RefundContext;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaAdminRefundStateSyncPort;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaQuestionProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaContentHashFactory;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExecuteQnaAdminRefundService unit test")
class ExecuteQnaAdminRefundServiceTest {

  @Mock private LoadQnaAdminReviewContextPort loadQnaAdminReviewContextPort;
  @Mock private QnaAdminRefundStateSyncPort qnaAdminRefundStateSyncPort;
  @Mock private PrepareQnaInternalRefundUseCase prepareQnaInternalRefundUseCase;

  @InjectMocks private ExecuteQnaAdminRefundService service;

  @Test
  @DisplayName("execute marks question pending refund before preparing internal refund")
  void execute_marksPendingRefundBeforePrepare() {
    ExecuteQnaAdminRefundCommand command = new ExecuteQnaAdminRefundCommand(999L, 101L);
    RefundContext context =
        new RefundContext(
            Optional.of(
                new LocalQuestion(
                    101L,
                    7L,
                    true,
                    LocalQuestionStatus.OPEN,
                    false,
                    false,
                    "질문 본문",
                    50L,
                    null)),
            Optional.of(onchainQuestion()),
            Optional.empty(),
            List.of(),
            new ExecutionAuthority("0x" + "2".repeat(40), true),
            new LoadExecutionInternalIssuerPolicyPort.ExecutionInternalIssuerPolicy(
                true, true, true));
    QnaExecutionIntentResult expected =
        new QnaExecutionIntentResult(
            new QnaExecutionIntentResult.Resource("QUESTION", "101", "PENDING_EXECUTION"),
            "QNA_ADMIN_REFUND",
            new QnaExecutionIntentResult.ExecutionIntent(
                "intent-1", "AWAITING_SIGNATURE", LocalDateTime.of(2026, 4, 21, 10, 0)),
            new QnaExecutionIntentResult.Execution("EIP1559", 1),
            null,
            false);

    when(loadQnaAdminReviewContextPort.loadRefundForUpdate(101L)).thenReturn(context);
    when(prepareQnaInternalRefundUseCase.execute(new PrepareAdminRefundCommand(101L, 7L)))
        .thenReturn(expected);

    QnaExecutionIntentResult actual = service.execute(command);

    assertThat(actual).isEqualTo(expected);
    var inOrder =
        inOrder(
            loadQnaAdminReviewContextPort,
            qnaAdminRefundStateSyncPort,
            prepareQnaInternalRefundUseCase);
    inOrder.verify(loadQnaAdminReviewContextPort).loadRefundForUpdate(101L);
    inOrder.verify(qnaAdminRefundStateSyncPort).beginPendingRefund(101L);
    inOrder
        .verify(prepareQnaInternalRefundUseCase)
        .execute(new PrepareAdminRefundCommand(101L, 7L));
  }

  private QnaQuestionProjection onchainQuestion() {
    return QnaQuestionProjection.create(
        101L,
        7L,
        QnaEscrowIdCodec.questionId(101L),
        "0x" + "1".repeat(40),
        new BigInteger("50000000000000000000"),
        QnaContentHashFactory.hash("질문 본문"));
  }
}
