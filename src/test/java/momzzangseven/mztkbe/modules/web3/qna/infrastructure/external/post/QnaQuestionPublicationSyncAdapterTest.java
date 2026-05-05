package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.post.application.dto.SyncQuestionPublicationStateCommand;
import momzzangseven.mztkbe.modules.post.application.port.in.ConfirmQuestionCreatedUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.FailQuestionCreateUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.post.QnaQuestionPublicationSyncAdapter.QnaQuestionPublicationSyncEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("QnaQuestionPublicationSyncAdapter unit test")
class QnaQuestionPublicationSyncAdapterTest {

  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private ConfirmQuestionCreatedUseCase confirmQuestionCreatedUseCase;
  @Mock private FailQuestionCreateUseCase failQuestionCreateUseCase;

  private QnaQuestionPublicationSyncAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter =
        new QnaQuestionPublicationSyncAdapter(
            eventPublisher, confirmQuestionCreatedUseCase, failQuestionCreateUseCase);
  }

  @Test
  @DisplayName("confirm sync publishes event instead of invoking post use case inline")
  void confirmSyncPublishesEvent() {
    adapter.confirmQuestionCreated(101L, "intent-1");

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue())
        .isEqualTo(new QnaQuestionPublicationSyncEvent.Confirmed(101L, "intent-1"));
  }

  @Test
  @DisplayName("after-commit confirmed event invokes post publication use case")
  void confirmedEventInvokesUseCase() {
    adapter.handleConfirmed(new QnaQuestionPublicationSyncEvent.Confirmed(101L, "intent-1"));

    verify(confirmQuestionCreatedUseCase)
        .confirmQuestionCreated(
            new SyncQuestionPublicationStateCommand(101L, "intent-1", null, null));
  }

  @Test
  @DisplayName("failure sync publishes event instead of invoking post use case inline")
  void failureSyncPublishesEvent() {
    adapter.failQuestionCreate(101L, "intent-1", ExecutionIntentStatus.EXPIRED, "expired");

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue())
        .isEqualTo(
            new QnaQuestionPublicationSyncEvent.Failed(101L, "intent-1", "EXPIRED", "expired"));
  }

  @Test
  @DisplayName("after-commit failed event invokes post publication use case")
  void failedEventInvokesUseCase() {
    adapter.handleFailed(
        new QnaQuestionPublicationSyncEvent.Failed(101L, "intent-1", "EXPIRED", "expired"));

    verify(failQuestionCreateUseCase)
        .failQuestionCreate(
            new SyncQuestionPublicationStateCommand(101L, "intent-1", "EXPIRED", "expired"));
  }
}
