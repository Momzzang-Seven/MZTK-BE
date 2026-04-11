package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.EnumSet;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.HandleTransferSucceededCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.MarkQuestionPostSolvedPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.QuestionRewardIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntent;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferTransactionReferenceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("HandleTransferSucceededService")
class HandleTransferSucceededServiceTest {

  @Mock private MarkQuestionPostSolvedPort markQuestionPostSolvedPort;
  @Mock private QuestionRewardIntentPersistencePort questionRewardIntentPersistencePort;

  @InjectMocks private HandleTransferSucceededService service;

  @Test
  @DisplayName("marks question post resolved when intent settles from submitted state")
  void execute_marksQuestionPostResolved() {
    when(questionRewardIntentPersistencePort.updateStatusIfCurrentIn(
            eq(77L),
            eq(QuestionRewardIntentStatus.SUCCEEDED),
            eq(
                EnumSet.of(
                    QuestionRewardIntentStatus.PREPARE_REQUIRED,
                    QuestionRewardIntentStatus.SUBMITTED))))
        .thenReturn(1);
    when(markQuestionPostSolvedPort.markSolved(77L)).thenReturn(1);

    service.execute(questionRewardCommand("77"));

    verify(markQuestionPostSolvedPort).markSolved(77L);
  }

  @Test
  @DisplayName("duplicate succeeded event stays idempotent when intent is already succeeded")
  void execute_duplicateSucceededEvent_keepsNoOpPath() {
    when(questionRewardIntentPersistencePort.updateStatusIfCurrentIn(
            eq(77L),
            eq(QuestionRewardIntentStatus.SUCCEEDED),
            eq(
                EnumSet.of(
                    QuestionRewardIntentStatus.PREPARE_REQUIRED,
                    QuestionRewardIntentStatus.SUBMITTED))))
        .thenReturn(0);
    when(questionRewardIntentPersistencePort.findByPostId(77L))
        .thenReturn(Optional.of(succeededIntent(77L)));
    when(markQuestionPostSolvedPort.markSolved(77L)).thenReturn(0);

    service.execute(questionRewardCommand("77"));

    verify(markQuestionPostSolvedPort).markSolved(77L);
  }

  @Test
  @DisplayName("skips post resolve update when intent is not settled")
  void execute_skipsResolveUpdate_whenIntentIsNotSettled() {
    when(questionRewardIntentPersistencePort.updateStatusIfCurrentIn(
            eq(77L),
            eq(QuestionRewardIntentStatus.SUCCEEDED),
            eq(
                EnumSet.of(
                    QuestionRewardIntentStatus.PREPARE_REQUIRED,
                    QuestionRewardIntentStatus.SUBMITTED))))
        .thenReturn(0);
    when(questionRewardIntentPersistencePort.findByPostId(77L))
        .thenReturn(Optional.of(failedIntent(77L)));

    service.execute(questionRewardCommand("77"));

    verify(markQuestionPostSolvedPort, never()).markSolved(any());
  }

  @Test
  @DisplayName("skips non-question reward transfers")
  void execute_skipsNonQuestionRewardTransfer() {
    service.execute(
        new HandleTransferSucceededCommand(
            104L,
            "reward:11:55",
            TransferTransactionReferenceType.LEVEL_UP_REWARD,
            "55",
            null,
            11L,
            "0xhash"));

    verify(questionRewardIntentPersistencePort, never()).updateStatusIfCurrentIn(any(), any(), any());
    verify(markQuestionPostSolvedPort, never()).markSolved(any());
  }

  private HandleTransferSucceededCommand questionRewardCommand(String referenceId) {
    return new HandleTransferSucceededCommand(
        103L,
        "domain:QUESTION_REWARD:" + referenceId + ":11",
        TransferTransactionReferenceType.USER_TO_USER,
        referenceId,
        11L,
        22L,
        "0xhash");
  }

  private QuestionRewardIntent succeededIntent(Long postId) {
    return QuestionRewardIntent.builder()
        .postId(postId)
        .acceptedCommentId(1001L)
        .fromUserId(11L)
        .toUserId(22L)
        .amountWei(BigInteger.ONE)
        .status(QuestionRewardIntentStatus.SUCCEEDED)
        .build();
  }

  private QuestionRewardIntent failedIntent(Long postId) {
    return QuestionRewardIntent.builder()
        .postId(postId)
        .acceptedCommentId(1001L)
        .fromUserId(11L)
        .toUserId(22L)
        .amountWei(BigInteger.ONE)
        .status(QuestionRewardIntentStatus.CANCELED)
        .build();
  }
}
