package momzzangseven.mztkbe.modules.answer.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadPostPort.PostContext;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnswerPostAdapter unit test")
class AnswerPostAdapterTest {

  @Mock private PostPersistencePort postPersistencePort;

  @InjectMocks private AnswerPostAdapter answerPostAdapter;

  @Test
  @DisplayName("loadPost() maps Post to PostContext")
  void loadPost_mapsPostToPostContext() {
    Post post =
        mock(
            Post.class,
            invocation ->
                Map.of(
                        "getId",
                        10L,
                        "getUserId",
                        20L,
                        "getIsSolved",
                        true,
                        "getType",
                        PostType.QUESTION)
                    .get(invocation.getMethod().getName()));
    when(postPersistencePort.loadPost(10L)).thenReturn(Optional.of(post));

    Optional<PostContext> result = answerPostAdapter.loadPost(10L);

    assertThat(result).isPresent();
    assertThat(result.get().postId()).isEqualTo(10L);
    assertThat(result.get().writerId()).isEqualTo(20L);
    assertThat(result.get().isSolved()).isTrue();
    assertThat(result.get().questionPost()).isTrue();
  }
}
