package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.answer.application.port.in.ConfirmAnswerUpdateUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.FailAnswerUpdateUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("QnaAnswerUpdateSyncAdapter")
class QnaAnswerUpdateSyncAdapterTest {

  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private ConfirmAnswerUpdateUseCase confirmAnswerUpdateUseCase;
  @Mock private FailAnswerUpdateUseCase failAnswerUpdateUseCase;

  @Test
  @DisplayName("confirmAnswerUpdate publishes an after-commit sync event")
  void confirmAnswerUpdatePublishesEvent() {
    QnaAnswerUpdateSyncAdapter adapter =
        new QnaAnswerUpdateSyncAdapter(
            eventPublisher, confirmAnswerUpdateUseCase, failAnswerUpdateUseCase);

    adapter.confirmAnswerUpdate(100L, 3L, "update-token", "intent-update");

    ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue())
        .isEqualTo(
            new QnaAnswerUpdateSyncAdapter.AnswerUpdateSyncEvent.Confirmed(
                100L, 3L, "update-token", "intent-update"));
  }

  @Test
  @DisplayName("handleConfirmed delegates to the answer update confirm use case")
  void handleConfirmedDelegatesToUseCase() {
    QnaAnswerUpdateSyncAdapter adapter =
        new QnaAnswerUpdateSyncAdapter(
            eventPublisher, confirmAnswerUpdateUseCase, failAnswerUpdateUseCase);

    adapter.handleConfirmed(
        new QnaAnswerUpdateSyncAdapter.AnswerUpdateSyncEvent.Confirmed(
            100L, 3L, "update-token", "intent-update"));

    verify(confirmAnswerUpdateUseCase)
        .confirmAnswerUpdate(
            argThat(
                command ->
                    command.answerId().equals(100L)
                        && command.updateVersion().equals(3L)
                        && command.updateToken().equals("update-token")
                        && command.executionIntentId().equals("intent-update")));
  }

  @Test
  @DisplayName("failAnswerUpdate publishes a failure sync event")
  void failAnswerUpdatePublishesEvent() {
    QnaAnswerUpdateSyncAdapter adapter =
        new QnaAnswerUpdateSyncAdapter(
            eventPublisher, confirmAnswerUpdateUseCase, failAnswerUpdateUseCase);

    adapter.failAnswerUpdate(100L, 3L, "update-token", "intent-update", "RPC_UNAVAILABLE");

    ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue())
        .isEqualTo(
            new QnaAnswerUpdateSyncAdapter.AnswerUpdateSyncEvent.Failed(
                100L, 3L, "update-token", "intent-update", "RPC_UNAVAILABLE"));
  }

  @Test
  @DisplayName("handleFailed delegates to the answer update failure use case")
  void handleFailedDelegatesToUseCase() {
    QnaAnswerUpdateSyncAdapter adapter =
        new QnaAnswerUpdateSyncAdapter(
            eventPublisher, confirmAnswerUpdateUseCase, failAnswerUpdateUseCase);

    adapter.handleFailed(
        new QnaAnswerUpdateSyncAdapter.AnswerUpdateSyncEvent.Failed(
            100L, 3L, "update-token", "intent-update", "RPC_UNAVAILABLE"));

    verify(failAnswerUpdateUseCase)
        .failAnswerUpdate(
            argThat(
                command ->
                    command.answerId().equals(100L)
                        && command.updateVersion().equals(3L)
                        && command.updateToken().equals("update-token")
                        && command.executionIntentId().equals("intent-update")
                        && command.failureReason().equals("RPC_UNAVAILABLE")));
  }
}
