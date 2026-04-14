package momzzangseven.mztkbe.modules.answer.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import momzzangseven.mztkbe.modules.post.application.port.in.GetAnswerLikeStatusUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnswerLikeAdapter unit test")
class AnswerLikeAdapterTest {

  @Mock private GetAnswerLikeStatusUseCase getAnswerLikeStatusUseCase;

  @InjectMocks private AnswerLikeAdapter answerLikeAdapter;

  @Test
  @DisplayName("countLikeByAnswerIds delegates to post answer like use case")
  void countLikeByAnswerIds_delegates() {
    when(getAnswerLikeStatusUseCase.countLikeByAnswerIds(List.of(1L, 2L)))
        .thenReturn(Map.of(1L, 3L));

    Map<Long, Long> result = answerLikeAdapter.countLikeByAnswerIds(List.of(1L, 2L));

    assertThat(result).containsEntry(1L, 3L);
    verify(getAnswerLikeStatusUseCase).countLikeByAnswerIds(List.of(1L, 2L));
  }

  @Test
  @DisplayName("loadLikedAnswerIds delegates to post answer like use case")
  void loadLikedAnswerIds_delegates() {
    when(getAnswerLikeStatusUseCase.loadLikedAnswerIds(List.of(1L, 2L), 99L))
        .thenReturn(Set.of(2L));

    Set<Long> result = answerLikeAdapter.loadLikedAnswerIds(List.of(1L, 2L), 99L);

    assertThat(result).containsExactly(2L);
    verify(getAnswerLikeStatusUseCase).loadLikedAnswerIds(List.of(1L, 2L), 99L);
  }
}
