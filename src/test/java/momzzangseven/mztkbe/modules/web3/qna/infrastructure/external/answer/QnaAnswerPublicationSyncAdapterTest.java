package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.answer.application.port.in.ConfirmAnswerSubmittedUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.FailAnswerSubmitUseCase;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionIntentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("QnaAnswerPublicationSyncAdapter")
class QnaAnswerPublicationSyncAdapterTest {

  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private ConfirmAnswerSubmittedUseCase confirmAnswerSubmittedUseCase;
  @Mock private FailAnswerSubmitUseCase failAnswerSubmitUseCase;

  @Test
  @DisplayName("confirmAnswerSubmitted publishes an after-commit sync event")
  void confirmAnswerSubmittedPublishesEvent() {
    QnaAnswerPublicationSyncAdapter adapter =
        new QnaAnswerPublicationSyncAdapter(
            eventPublisher, confirmAnswerSubmittedUseCase, failAnswerSubmitUseCase);

    adapter.confirmAnswerSubmitted(100L, "intent-create");

    ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue())
        .isEqualTo(
            new QnaAnswerPublicationSyncAdapter.AnswerPublicationSyncEvent.Confirmed(
                100L, "intent-create"));
  }

  @Test
  @DisplayName("handleConfirmed delegates to the answer publication confirm use case")
  void handleConfirmedDelegatesToUseCase() {
    QnaAnswerPublicationSyncAdapter adapter =
        new QnaAnswerPublicationSyncAdapter(
            eventPublisher, confirmAnswerSubmittedUseCase, failAnswerSubmitUseCase);

    adapter.handleConfirmed(
        new QnaAnswerPublicationSyncAdapter.AnswerPublicationSyncEvent.Confirmed(
            100L, "intent-create"));

    verify(confirmAnswerSubmittedUseCase)
        .confirmAnswerSubmitted(
            argThat(
                command ->
                    command.answerId().equals(100L)
                        && command.executionIntentId().equals("intent-create")));
  }

  @Test
  @DisplayName("failAnswerSubmit publishes a failure sync event")
  void failAnswerSubmitPublishesEvent() {
    QnaAnswerPublicationSyncAdapter adapter =
        new QnaAnswerPublicationSyncAdapter(
            eventPublisher, confirmAnswerSubmittedUseCase, failAnswerSubmitUseCase);

    adapter.failAnswerSubmit(
        100L, "intent-create", QnaExecutionIntentStatus.FAILED_ONCHAIN, "RPC_UNAVAILABLE");

    ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue())
        .isEqualTo(
            new QnaAnswerPublicationSyncAdapter.AnswerPublicationSyncEvent.Failed(
                100L, "intent-create", "FAILED_ONCHAIN", "RPC_UNAVAILABLE"));
  }

  @Test
  @DisplayName("handleFailed delegates to the answer publication failure use case")
  void handleFailedDelegatesToUseCase() {
    QnaAnswerPublicationSyncAdapter adapter =
        new QnaAnswerPublicationSyncAdapter(
            eventPublisher, confirmAnswerSubmittedUseCase, failAnswerSubmitUseCase);

    adapter.handleFailed(
        new QnaAnswerPublicationSyncAdapter.AnswerPublicationSyncEvent.Failed(
            100L, "intent-create", "FAILED_ONCHAIN", "RPC_UNAVAILABLE"));

    verify(failAnswerSubmitUseCase)
        .failAnswerSubmit(
            argThat(
                command ->
                    command.answerId().equals(100L)
                        && command.executionIntentId().equals("intent-create")
                        && command.terminalStatus().equals("FAILED_ONCHAIN")
                        && command.failureReason().equals("RPC_UNAVAILABLE")));
  }
}
