package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transaction.domain.event.Web3TransactionFailedOnchainEvent;
import momzzangseven.mztkbe.modules.web3.transaction.domain.event.Web3TransactionSucceededEvent;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.HandleTransferFailedOnchainCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.HandleTransferSucceededCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.HandleTransferFailedOnchainUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.HandleTransferSucceededUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.CompensateTransferFailurePort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.MarkQuestionPostSolvedPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.QuestionRewardIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.application.service.HandleTransferFailedOnchainService;
import momzzangseven.mztkbe.modules.web3.transfer.application.service.HandleTransferSucceededService;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntent;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferTransactionReferenceType;
import org.junit.jupiter.api.Test;

class Web3TransferFailedOnchainEventHandlerTest {

  @Test
  void failedHandler_delegatesMappedCommandToUseCase() {
    HandleTransferFailedOnchainUseCase useCase = mock(HandleTransferFailedOnchainUseCase.class);
    Web3TransferFailedOnchainEventHandler handler =
        new Web3TransferFailedOnchainEventHandler(useCase);

    Web3TransactionFailedOnchainEvent event =
        new Web3TransactionFailedOnchainEvent(
            100L,
            "domain:QUESTION_REWARD:77:11",
            Web3ReferenceType.USER_TO_USER,
            "77",
            11L,
            22L,
            "0x1234567890123456789012345678901234567890123456789012345678901234",
            "RECEIPT_STATUS_0");

    handler.handle(event);

    verify(useCase)
        .execute(
            new HandleTransferFailedOnchainCommand(
                100L,
                "domain:QUESTION_REWARD:77:11",
                TransferTransactionReferenceType.USER_TO_USER,
                "77",
                11L,
                22L,
                "0x1234567890123456789012345678901234567890123456789012345678901234",
                "RECEIPT_STATUS_0"));
  }

  @Test
  void succeededHandler_delegatesMappedCommandToUseCase() {
    HandleTransferSucceededUseCase useCase = mock(HandleTransferSucceededUseCase.class);
    Web3TransferSucceededEventHandler handler = new Web3TransferSucceededEventHandler(useCase);

    Web3TransactionSucceededEvent event =
        new Web3TransactionSucceededEvent(
            103L,
            "domain:QUESTION_REWARD:77:11",
            Web3ReferenceType.USER_TO_USER,
            "77",
            11L,
            22L,
            "0x1234567890123456789012345678901234567890123456789012345678901234");

    handler.handle(event);

    verify(useCase)
        .execute(
            new HandleTransferSucceededCommand(
                103L,
                "domain:QUESTION_REWARD:77:11",
                TransferTransactionReferenceType.USER_TO_USER,
                "77",
                11L,
                22L,
                "0x1234567890123456789012345678901234567890123456789012345678901234"));
  }

  @Test
  void handleTransferFailedOnchainService_callsCompensator_whenDomainMatched() {
    CompensateTransferFailurePort compensator = mock(CompensateTransferFailurePort.class);
    when(compensator.supports(DomainReferenceType.QUESTION_REWARD)).thenReturn(true);
    HandleTransferFailedOnchainService service =
        new HandleTransferFailedOnchainService(List.of(compensator));
    HandleTransferFailedOnchainCommand command =
        new HandleTransferFailedOnchainCommand(
            100L,
            "domain:QUESTION_REWARD:77:11",
            TransferTransactionReferenceType.USER_TO_USER,
            "77",
            11L,
            22L,
            "0xhash",
            "RECEIPT_STATUS_0");

    service.execute(command);

    verify(compensator).compensate(command);
  }

  @Test
  void handleTransferFailedOnchainService_skips_whenNoCompensatorSupportsDomain() {
    CompensateTransferFailurePort compensator = mock(CompensateTransferFailurePort.class);
    when(compensator.supports(DomainReferenceType.QUESTION_REWARD)).thenReturn(false);
    HandleTransferFailedOnchainService service =
        new HandleTransferFailedOnchainService(List.of(compensator));

    service.execute(
        new HandleTransferFailedOnchainCommand(
            101L,
            "domain:QUESTION_REWARD:88:11",
            TransferTransactionReferenceType.USER_TO_USER,
            "88",
            11L,
            22L,
            "0xhash",
            "RECEIPT_STATUS_0"));

    verify(compensator, never()).compensate(any());
  }

  @Test
  void handleTransferSucceededService_marksQuestionSolved_whenQuestionRewardDomain() {
    MarkQuestionPostSolvedPort markQuestionPostSolvedPort = mock(MarkQuestionPostSolvedPort.class);
    QuestionRewardIntentPersistencePort questionRewardIntentPersistencePort =
        mock(QuestionRewardIntentPersistencePort.class);
    when(questionRewardIntentPersistencePort.updateStatusIfCurrentIn(
            eq(77L),
            eq(QuestionRewardIntentStatus.SUCCEEDED),
            eq(
                EnumSet.of(
                    QuestionRewardIntentStatus.PREPARE_REQUIRED,
                    QuestionRewardIntentStatus.SUBMITTED))))
        .thenReturn(1);
    when(markQuestionPostSolvedPort.markSolved(77L)).thenReturn(1);
    HandleTransferSucceededService service =
        new HandleTransferSucceededService(
            markQuestionPostSolvedPort, questionRewardIntentPersistencePort);

    service.execute(
        new HandleTransferSucceededCommand(
            103L,
            "domain:QUESTION_REWARD:77:11",
            TransferTransactionReferenceType.USER_TO_USER,
            "77",
            11L,
            22L,
            "0xhash"));

    verify(questionRewardIntentPersistencePort)
        .updateStatusIfCurrentIn(
            77L,
            QuestionRewardIntentStatus.SUCCEEDED,
            EnumSet.of(
                QuestionRewardIntentStatus.PREPARE_REQUIRED, QuestionRewardIntentStatus.SUBMITTED));
    verify(markQuestionPostSolvedPort).markSolved(77L);
  }

  @Test
  void handleTransferSucceededService_skips_whenDomainIsNotQuestionReward() {
    MarkQuestionPostSolvedPort markQuestionPostSolvedPort = mock(MarkQuestionPostSolvedPort.class);
    QuestionRewardIntentPersistencePort questionRewardIntentPersistencePort =
        mock(QuestionRewardIntentPersistencePort.class);
    HandleTransferSucceededService service =
        new HandleTransferSucceededService(
            markQuestionPostSolvedPort, questionRewardIntentPersistencePort);

    service.execute(
        new HandleTransferSucceededCommand(
            104L,
            "reward:11:55",
            TransferTransactionReferenceType.LEVEL_UP_REWARD,
            "55",
            null,
            11L,
            "0xhash"));

    verify(questionRewardIntentPersistencePort, never())
        .updateStatusIfCurrentIn(any(), any(), any());
    verify(markQuestionPostSolvedPort, never()).markSolved(any());
  }

  @Test
  void handleTransferSucceededService_skipsPostSolvedUpdate_whenIntentIsNotSettled() {
    MarkQuestionPostSolvedPort markQuestionPostSolvedPort = mock(MarkQuestionPostSolvedPort.class);
    QuestionRewardIntentPersistencePort questionRewardIntentPersistencePort =
        mock(QuestionRewardIntentPersistencePort.class);
    when(questionRewardIntentPersistencePort.updateStatusIfCurrentIn(
            eq(77L),
            eq(QuestionRewardIntentStatus.SUCCEEDED),
            eq(
                EnumSet.of(
                    QuestionRewardIntentStatus.PREPARE_REQUIRED,
                    QuestionRewardIntentStatus.SUBMITTED))))
        .thenReturn(0);
    when(questionRewardIntentPersistencePort.findByPostId(77L))
        .thenReturn(
            Optional.of(
                QuestionRewardIntent.builder()
                    .postId(77L)
                    .acceptedCommentId(1001L)
                    .fromUserId(11L)
                    .toUserId(22L)
                    .amountWei(BigInteger.ONE)
                    .status(QuestionRewardIntentStatus.CANCELED)
                    .build()));
    HandleTransferSucceededService service =
        new HandleTransferSucceededService(
            markQuestionPostSolvedPort, questionRewardIntentPersistencePort);

    service.execute(
        new HandleTransferSucceededCommand(
            106L,
            "domain:QUESTION_REWARD:77:11",
            TransferTransactionReferenceType.USER_TO_USER,
            "77",
            11L,
            22L,
            "0xhash"));

    verify(markQuestionPostSolvedPort, never()).markSolved(any());
  }

  @Test
  void questionRewardFailureCompensator_marksIntentFailed_whenReferenceIdIsValid() {
    QuestionRewardIntentPersistencePort questionRewardIntentPersistencePort =
        mock(QuestionRewardIntentPersistencePort.class);
    when(questionRewardIntentPersistencePort.updateStatusIfCurrentIn(
            eq(77L), eq(QuestionRewardIntentStatus.FAILED_ONCHAIN), any()))
        .thenReturn(1);
    QuestionRewardFailureCompensator compensator =
        new QuestionRewardFailureCompensator(questionRewardIntentPersistencePort);

    compensator.compensate(
        new HandleTransferFailedOnchainCommand(
            106L,
            "domain:QUESTION_REWARD:77:11",
            TransferTransactionReferenceType.USER_TO_USER,
            "77",
            11L,
            22L,
            "0xhash",
            "RECEIPT_STATUS_0"));

    verify(questionRewardIntentPersistencePort)
        .updateStatusIfCurrentIn(
            77L,
            QuestionRewardIntentStatus.FAILED_ONCHAIN,
            EnumSet.of(
                QuestionRewardIntentStatus.PREPARE_REQUIRED, QuestionRewardIntentStatus.SUBMITTED));
  }

  @Test
  void questionRewardFailureCompensator_skips_whenReferenceIdIsInvalid() {
    QuestionRewardIntentPersistencePort questionRewardIntentPersistencePort =
        mock(QuestionRewardIntentPersistencePort.class);
    QuestionRewardFailureCompensator compensator =
        new QuestionRewardFailureCompensator(questionRewardIntentPersistencePort);

    compensator.compensate(
        new HandleTransferFailedOnchainCommand(
            108L,
            "domain:QUESTION_REWARD:bad:11",
            TransferTransactionReferenceType.USER_TO_USER,
            "bad",
            11L,
            22L,
            "0xhash",
            "RECEIPT_STATUS_0"));

    verifyNoInteractions(questionRewardIntentPersistencePort);
  }
}
