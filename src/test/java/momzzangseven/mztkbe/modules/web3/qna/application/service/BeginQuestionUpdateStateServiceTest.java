package momzzangseven.mztkbe.modules.web3.qna.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.BeginQuestionUpdateStateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaQuestionUpdateStatePersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaQuestionUpdateState;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaQuestionUpdateStateStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BeginQuestionUpdateStateService unit test")
class BeginQuestionUpdateStateServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-04-12T01:00:00Z"), ZoneId.of("Asia/Seoul"));

  @Mock private QnaQuestionUpdateStatePersistencePort statePersistencePort;

  @Test
  @DisplayName("first question update creates version 1 state")
  void beginCreatesFirstVersion() {
    BeginQuestionUpdateStateService service =
        new BeginQuestionUpdateStateService(statePersistencePort, CLOCK);
    when(statePersistencePort.findLatestByPostIdForUpdate(101L)).thenReturn(Optional.empty());
    when(statePersistencePort.save(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var result = service.begin(new BeginQuestionUpdateStateCommand(101L, 7L, hash("a")));

    assertThat(result.updateVersion()).isEqualTo(1L);
    assertThat(result.updateToken()).isNotBlank();
    assertThat(result.expectedQuestionHash()).isEqualTo(hash("a"));
    verify(statePersistencePort).markNonTerminalStaleByPostId(101L);
  }

  @Test
  @DisplayName(
      "next question update marks previous non-terminal state stale and saves next version")
  void beginAdvancesVersionAndMarksPreviousStale() {
    BeginQuestionUpdateStateService service =
        new BeginQuestionUpdateStateService(statePersistencePort, CLOCK);
    when(statePersistencePort.findLatestByPostIdForUpdate(101L))
        .thenReturn(Optional.of(existingState(2L)));
    when(statePersistencePort.save(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.begin(new BeginQuestionUpdateStateCommand(101L, 7L, hash("b")));

    ArgumentCaptor<QnaQuestionUpdateState> captor =
        ArgumentCaptor.forClass(QnaQuestionUpdateState.class);
    verify(statePersistencePort).markNonTerminalStaleByPostId(101L);
    verify(statePersistencePort).save(captor.capture());
    assertThat(captor.getValue().getUpdateVersion()).isEqualTo(3L);
    assertThat(captor.getValue().getStatus()).isEqualTo(QnaQuestionUpdateStateStatus.PREPARING);
    assertThat(captor.getValue().getExpectedQuestionHash()).isEqualTo(hash("b"));
  }

  private QnaQuestionUpdateState existingState(Long version) {
    return QnaQuestionUpdateState.builder()
        .postId(101L)
        .requesterUserId(7L)
        .updateVersion(version)
        .updateToken("token-" + version)
        .expectedQuestionHash(hash("old"))
        .status(QnaQuestionUpdateStateStatus.INTENT_BOUND)
        .createdAt(LocalDateTime.of(2026, 4, 12, 10, 0))
        .updatedAt(LocalDateTime.of(2026, 4, 12, 10, 0))
        .build();
  }

  private String hash(String suffix) {
    return "0x" + suffix.repeat(64);
  }
}
