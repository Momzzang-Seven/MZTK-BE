package momzzangseven.mztkbe.modules.post.infrastructure.external.answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.answer.application.port.in.MarkAnswerAcceptedUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadAcceptedAnswerPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AcceptedAnswerAdapter unit test")
class AcceptedAnswerAdapterTest {

  @Mock private LoadAnswerPort loadAnswerPort;
  @Mock private MarkAnswerAcceptedUseCase markAnswerAcceptedUseCase;

  @InjectMocks private AcceptedAnswerAdapter acceptedAnswerAdapter;

  @Test
  @DisplayName("maps answer module domain object to accepted answer info")
  void loadAcceptedAnswer_mapsAnswer() {
    Answer answer =
        Answer.builder()
            .id(20L)
            .postId(10L)
            .userId(2L)
            .content("content")
            .isAccepted(false)
            .createdAt(LocalDateTime.of(2026, 1, 1, 10, 0))
            .updatedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
            .build();
    when(loadAnswerPort.loadAnswer(20L)).thenReturn(Optional.of(answer));

    Optional<LoadAcceptedAnswerPort.AcceptedAnswerInfo> result =
        acceptedAnswerAdapter.loadAcceptedAnswer(20L);

    assertThat(result).isPresent();
    assertThat(result.get().answerId()).isEqualTo(20L);
    assertThat(result.get().postId()).isEqualTo(10L);
    assertThat(result.get().userId()).isEqualTo(2L);
  }

  @Test
  @DisplayName("delegates accepted answer state sync to answer module use case")
  void markAccepted_delegatesToUseCase() {
    acceptedAnswerAdapter.markAccepted(20L);

    verify(markAnswerAcceptedUseCase).markAccepted(20L);
  }
}
