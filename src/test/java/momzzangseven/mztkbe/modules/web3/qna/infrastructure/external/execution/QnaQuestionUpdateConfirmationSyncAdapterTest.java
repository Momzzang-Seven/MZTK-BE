package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ReplayConfirmedExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ReplayConfirmedExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaQuestionUpdateStatePersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaQuestionUpdateState;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaQuestionUpdateStateStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("QnaQuestionUpdateConfirmationSyncAdapter unit test")
class QnaQuestionUpdateConfirmationSyncAdapterTest {

  @Mock private ReplayConfirmedExecutionIntentUseCase replayConfirmedExecutionIntentUseCase;
  @Mock private QnaQuestionUpdateStatePersistencePort statePersistencePort;

  @InjectMocks private QnaQuestionUpdateConfirmationSyncAdapter adapter;

  @Test
  @DisplayName("confirmed question update intent is replayed through the qna action handler")
  void syncConfirmedQuestionUpdateReplaysConfirmedQuestionUpdate() {
    when(replayConfirmedExecutionIntentUseCase.execute(
            new ReplayConfirmedExecutionIntentCommand("intent-1", "QNA_QUESTION_UPDATE")))
        .thenReturn(true);
    when(statePersistencePort.findByExecutionIntentPublicIdForUpdate("intent-1"))
        .thenReturn(Optional.of(state(QnaQuestionUpdateStateStatus.CONFIRMED)));

    boolean result = adapter.syncConfirmedQuestionUpdate("intent-1");

    assertThat(result).isTrue();
    verify(replayConfirmedExecutionIntentUseCase)
        .execute(new ReplayConfirmedExecutionIntentCommand("intent-1", "QNA_QUESTION_UPDATE"));
  }

  @Test
  @DisplayName("confirmed question update sync returns false when state stays intent bound")
  void syncConfirmedQuestionUpdateReturnsFalseWhenStillIntentBound() {
    when(replayConfirmedExecutionIntentUseCase.execute(
            new ReplayConfirmedExecutionIntentCommand("intent-1", "QNA_QUESTION_UPDATE")))
        .thenReturn(true);
    when(statePersistencePort.findByExecutionIntentPublicIdForUpdate("intent-1"))
        .thenReturn(Optional.of(state(QnaQuestionUpdateStateStatus.INTENT_BOUND)));

    boolean result = adapter.syncConfirmedQuestionUpdate("intent-1");

    assertThat(result).isFalse();
    verify(replayConfirmedExecutionIntentUseCase)
        .execute(new ReplayConfirmedExecutionIntentCommand("intent-1", "QNA_QUESTION_UPDATE"));
  }

  @Test
  @DisplayName("non replayed question update intent is skipped")
  void syncConfirmedQuestionUpdateSkipsWhenReplaySkipped() {
    when(replayConfirmedExecutionIntentUseCase.execute(
            new ReplayConfirmedExecutionIntentCommand("intent-1", "QNA_QUESTION_UPDATE")))
        .thenReturn(false);

    boolean result = adapter.syncConfirmedQuestionUpdate("intent-1");

    assertThat(result).isFalse();
    verify(replayConfirmedExecutionIntentUseCase)
        .execute(new ReplayConfirmedExecutionIntentCommand("intent-1", "QNA_QUESTION_UPDATE"));
    verify(statePersistencePort, never()).findByExecutionIntentPublicIdForUpdate(any());
  }

  private QnaQuestionUpdateState state(QnaQuestionUpdateStateStatus status) {
    return QnaQuestionUpdateState.builder()
        .postId(101L)
        .requesterUserId(7L)
        .updateVersion(1L)
        .updateToken("token-1")
        .expectedQuestionHash("0x" + "a".repeat(64))
        .executionIntentPublicId("intent-1")
        .status(status)
        .createdAt(LocalDateTime.of(2026, 4, 12, 10, 0))
        .updatedAt(LocalDateTime.of(2026, 4, 12, 10, 1))
        .build();
  }
}
