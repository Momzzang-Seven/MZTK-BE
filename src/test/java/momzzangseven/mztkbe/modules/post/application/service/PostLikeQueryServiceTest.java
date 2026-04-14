package momzzangseven.mztkbe.modules.post.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import momzzangseven.mztkbe.modules.post.application.port.out.PostLikePersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.PostLikeTargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostLikeQueryService unit test")
class PostLikeQueryServiceTest {

  @Mock private PostLikePersistencePort postLikePersistencePort;

  @InjectMocks private PostLikeQueryService postLikeQueryService;

  @Test
  @DisplayName("countLikeByAnswerIds delegates answer like count query to persistence")
  void countLikeByAnswerIds_success() {
    when(postLikePersistencePort.countByTargetIds(PostLikeTargetType.ANSWER, List.of(1L, 2L)))
        .thenReturn(Map.of(1L, 3L));

    Map<Long, Long> result = postLikeQueryService.countLikeByAnswerIds(List.of(1L, 2L));

    assertThat(result).containsEntry(1L, 3L);
    verify(postLikePersistencePort).countByTargetIds(PostLikeTargetType.ANSWER, List.of(1L, 2L));
  }

  @Test
  @DisplayName("loadLikedAnswerIds delegates liked-answer query to persistence")
  void loadLikedAnswerIds_success() {
    when(postLikePersistencePort.findLikedTargetIds(
            PostLikeTargetType.ANSWER, List.of(1L, 2L), 99L))
        .thenReturn(Set.of(2L));

    Set<Long> result = postLikeQueryService.loadLikedAnswerIds(List.of(1L, 2L), 99L);

    assertThat(result).containsExactly(2L);
    verify(postLikePersistencePort)
        .findLikedTargetIds(PostLikeTargetType.ANSWER, List.of(1L, 2L), 99L);
  }
}
