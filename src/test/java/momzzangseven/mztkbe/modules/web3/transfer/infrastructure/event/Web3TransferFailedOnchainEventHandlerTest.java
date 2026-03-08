package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.transaction.domain.event.Web3TransactionFailedOnchainEvent;
import momzzangseven.mztkbe.modules.web3.transaction.domain.event.Web3TransactionSucceededEvent;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.MarkQuestionPostSolvedPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.QuestionRewardIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.application.rollback.DomainTransferFailureCompensator;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntent;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;
import org.junit.jupiter.api.Test;

class Web3TransferFailedOnchainEventHandlerTest {

  @Test
  void handle_callsCompensator_whenDomainMatched() {
    DomainTransferFailureCompensator compensator = mock(DomainTransferFailureCompensator.class);
    when(compensator.supports(DomainReferenceType.QUESTION_REWARD)).thenReturn(true);
    Web3TransferFailedOnchainEventHandler handler =
        new Web3TransferFailedOnchainEventHandler(List.of(compensator));

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

    verify(compensator).compensate(event);
  }

  @Test
  void handle_skips_whenNoCompensatorSupportsDomain() {
    DomainTransferFailureCompensator compensator = mock(DomainTransferFailureCompensator.class);
    when(compensator.supports(DomainReferenceType.QUESTION_REWARD)).thenReturn(false);
    Web3TransferFailedOnchainEventHandler handler =
        new Web3TransferFailedOnchainEventHandler(List.of(compensator));

    Web3TransactionFailedOnchainEvent event =
        new Web3TransactionFailedOnchainEvent(
            101L,
            "domain:QUESTION_REWARD:88:11",
            Web3ReferenceType.USER_TO_USER,
            "88",
            11L,
            22L,
            "0x1234567890123456789012345678901234567890123456789012345678901234",
            "RECEIPT_STATUS_0");

    handler.handle(event);

    verify(compensator, never()).compensate(any());
  }

  @Test
  void handle_resolvesLevelUpDomainFromReferenceType_whenIdempotencyKeyIsLegacyRewardFormat() {
    DomainTransferFailureCompensator compensator = mock(DomainTransferFailureCompensator.class);
    when(compensator.supports(DomainReferenceType.LEVEL_UP_REWARD)).thenReturn(true);
    Web3TransferFailedOnchainEventHandler handler =
        new Web3TransferFailedOnchainEventHandler(List.of(compensator));

    Web3TransactionFailedOnchainEvent event =
        new Web3TransactionFailedOnchainEvent(
            102L,
            "reward:11:55",
            Web3ReferenceType.LEVEL_UP_REWARD,
            "55",
            null,
            11L,
            "0x1234567890123456789012345678901234567890123456789012345678901234",
            "RECEIPT_STATUS_0");

    handler.handle(event);

    verify(compensator).compensate(event);
  }

  @Test
  void handle_skips_whenDomainCannotBeResolved() {
    DomainTransferFailureCompensator compensator = mock(DomainTransferFailureCompensator.class);
    when(compensator.supports(DomainReferenceType.QUESTION_REWARD)).thenReturn(true);
    Web3TransferFailedOnchainEventHandler handler =
        new Web3TransferFailedOnchainEventHandler(List.of(compensator));

    Web3TransactionFailedOnchainEvent event =
        new Web3TransactionFailedOnchainEvent(
            103L,
            "legacy-key",
            Web3ReferenceType.USER_TO_USER,
            "77",
            11L,
            22L,
            "0x1234567890123456789012345678901234567890123456789012345678901234",
            "RECEIPT_STATUS_0");

    handler.handle(event);

    verify(compensator, never()).compensate(any());
  }

  @Test
  void succeededHandler_marksQuestionSolved_whenQuestionRewardDomain() {
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
    Web3TransferSucceededEventHandler handler =
        new Web3TransferSucceededEventHandler(
            markQuestionPostSolvedPort, questionRewardIntentPersistencePort);

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

    verify(questionRewardIntentPersistencePort)
        .updateStatusIfCurrentIn(
            77L,
            QuestionRewardIntentStatus.SUCCEEDED,
            EnumSet.of(
                QuestionRewardIntentStatus.PREPARE_REQUIRED, QuestionRewardIntentStatus.SUBMITTED));
    verify(markQuestionPostSolvedPort).markSolved(77L);
  }

  @Test
  void succeededHandler_skips_whenDomainIsNotQuestionReward() {
    MarkQuestionPostSolvedPort markQuestionPostSolvedPort = mock(MarkQuestionPostSolvedPort.class);
    QuestionRewardIntentPersistencePort questionRewardIntentPersistencePort =
        mock(QuestionRewardIntentPersistencePort.class);
    Web3TransferSucceededEventHandler handler =
        new Web3TransferSucceededEventHandler(
            markQuestionPostSolvedPort, questionRewardIntentPersistencePort);

    Web3TransactionSucceededEvent event =
        new Web3TransactionSucceededEvent(
            104L,
            "reward:11:55",
            Web3ReferenceType.LEVEL_UP_REWARD,
            "55",
            null,
            11L,
            "0x1234567890123456789012345678901234567890123456789012345678901234");

    handler.handle(event);

    verify(questionRewardIntentPersistencePort, never())
        .updateStatusIfCurrentIn(any(), any(), any());
    verify(markQuestionPostSolvedPort, never()).markSolved(any());
  }

  @Test
  void succeededHandler_skips_whenQuestionReferenceIdIsInvalid() {
    MarkQuestionPostSolvedPort markQuestionPostSolvedPort = mock(MarkQuestionPostSolvedPort.class);
    QuestionRewardIntentPersistencePort questionRewardIntentPersistencePort =
        mock(QuestionRewardIntentPersistencePort.class);
    Web3TransferSucceededEventHandler handler =
        new Web3TransferSucceededEventHandler(
            markQuestionPostSolvedPort, questionRewardIntentPersistencePort);

    Web3TransactionSucceededEvent event =
        new Web3TransactionSucceededEvent(
            105L,
            "domain:QUESTION_REWARD:bad-ref:11",
            Web3ReferenceType.USER_TO_USER,
            "bad-ref",
            11L,
            22L,
            "0x1234567890123456789012345678901234567890123456789012345678901234");

    handler.handle(event);

    verify(questionRewardIntentPersistencePort, never())
        .updateStatusIfCurrentIn(any(), any(), any());
    verify(markQuestionPostSolvedPort, never()).markSolved(any());
  }

  @Test
  void succeededHandler_skipsPostSolvedUpdate_whenIntentIsNotSettled() {
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
            java.util.Optional.of(
                QuestionRewardIntent.builder()
                    .postId(77L)
                    .acceptedCommentId(1001L)
                    .fromUserId(11L)
                    .toUserId(22L)
                    .amountWei(java.math.BigInteger.ONE)
                    .status(QuestionRewardIntentStatus.CANCELED)
                    .build()));

    Web3TransferSucceededEventHandler handler =
        new Web3TransferSucceededEventHandler(
            markQuestionPostSolvedPort, questionRewardIntentPersistencePort);

    Web3TransactionSucceededEvent event =
        new Web3TransactionSucceededEvent(
            106L,
            "domain:QUESTION_REWARD:77:11",
            Web3ReferenceType.USER_TO_USER,
            "77",
            11L,
            22L,
            "0x1234567890123456789012345678901234567890123456789012345678901234");

    handler.handle(event);

    verify(markQuestionPostSolvedPort, never()).markSolved(any());
  }

  @Test
  void succeededHandler_marksQuestionSolved_whenIntentAlreadySucceeded() {
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
            java.util.Optional.of(
                QuestionRewardIntent.builder()
                    .postId(77L)
                    .acceptedCommentId(1001L)
                    .fromUserId(11L)
                    .toUserId(22L)
                    .amountWei(java.math.BigInteger.ONE)
                    .status(QuestionRewardIntentStatus.SUCCEEDED)
                    .build()));
    when(markQuestionPostSolvedPort.markSolved(77L)).thenReturn(1);
    Web3TransferSucceededEventHandler handler =
        new Web3TransferSucceededEventHandler(
            markQuestionPostSolvedPort, questionRewardIntentPersistencePort);

    Web3TransactionSucceededEvent event =
        new Web3TransactionSucceededEvent(
            107L,
            "domain:QUESTION_REWARD:77:11",
            Web3ReferenceType.USER_TO_USER,
            "77",
            11L,
            22L,
            "0x1234567890123456789012345678901234567890123456789012345678901234");

    handler.handle(event);

    verify(markQuestionPostSolvedPort).markSolved(77L);
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

    Web3TransactionFailedOnchainEvent event =
        new Web3TransactionFailedOnchainEvent(
            106L,
            "domain:QUESTION_REWARD:77:11",
            Web3ReferenceType.USER_TO_USER,
            "77",
            11L,
            22L,
            "0x1234567890123456789012345678901234567890123456789012345678901234",
            "RECEIPT_STATUS_0");

    compensator.compensate(event);

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

    Web3TransactionFailedOnchainEvent event =
        new Web3TransactionFailedOnchainEvent(
            108L,
            "domain:QUESTION_REWARD:bad:11",
            Web3ReferenceType.USER_TO_USER,
            "bad",
            11L,
            22L,
            "0x1234567890123456789012345678901234567890123456789012345678901234",
            "RECEIPT_STATUS_0");

    compensator.compensate(event);

    verifyNoInteractions(questionRewardIntentPersistencePort);
  }

  @Test
  void questionRewardFailureCompensator_noop_whenNoMutableIntentRows() {
    QuestionRewardIntentPersistencePort questionRewardIntentPersistencePort =
        mock(QuestionRewardIntentPersistencePort.class);
    when(questionRewardIntentPersistencePort.updateStatusIfCurrentIn(
            eq(77L), eq(QuestionRewardIntentStatus.FAILED_ONCHAIN), any()))
        .thenReturn(0);
    QuestionRewardFailureCompensator compensator =
        new QuestionRewardFailureCompensator(questionRewardIntentPersistencePort);

    Web3TransactionFailedOnchainEvent event =
        new Web3TransactionFailedOnchainEvent(
            109L,
            "domain:QUESTION_REWARD:77:11",
            Web3ReferenceType.USER_TO_USER,
            "77",
            11L,
            22L,
            "0x1234567890123456789012345678901234567890123456789012345678901234",
            "RECEIPT_STATUS_0");

    compensator.compensate(event);

    verify(questionRewardIntentPersistencePort)
        .updateStatusIfCurrentIn(
            77L,
            QuestionRewardIntentStatus.FAILED_ONCHAIN,
            EnumSet.of(
                QuestionRewardIntentStatus.PREPARE_REQUIRED, QuestionRewardIntentStatus.SUBMITTED));
  }
}
