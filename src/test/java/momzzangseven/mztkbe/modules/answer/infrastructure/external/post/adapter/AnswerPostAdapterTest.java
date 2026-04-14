package momzzangseven.mztkbe.modules.answer.infrastructure.external.post.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadPostPort.PostContext;
import momzzangseven.mztkbe.modules.post.application.port.in.GetPostContextUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnswerPostAdapter unit test")
class AnswerPostAdapterTest {

  @Mock private GetPostContextUseCase getPostContextUseCase;

  @InjectMocks private AnswerPostAdapter answerPostAdapter;

  @Test
  @DisplayName("loadPost() maps status-derived solved context to answer PostContext")
  void loadPost_mapsPostToPostContext() {
    when(getPostContextUseCase.getPostContext(10L))
        .thenReturn(Optional.of(new GetPostContextUseCase.PostContext(10L, 20L, true, true)));

    Optional<PostContext> result = answerPostAdapter.loadPost(10L);

    assertThat(result).isPresent();
    assertThat(result.get().postId()).isEqualTo(10L);
    assertThat(result.get().writerId()).isEqualTo(20L);
    assertThat(result.get().isSolved()).isTrue();
    assertThat(result.get().questionPost()).isTrue();
  }
}
