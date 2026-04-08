package momzzangseven.mztkbe.modules.post.application.service;

import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.post.application.port.out.PostLikePersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.PostLikeTargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostLikeCleanupService unit test")
class PostLikeCleanupServiceTest {

  @Mock private PostLikePersistencePort postLikePersistencePort;

  @InjectMocks private PostLikeCleanupService postLikeCleanupService;

  @Test
  @DisplayName("deletePostLikes deletes post likes by target")
  void deletePostLikes_success() {
    postLikeCleanupService.deletePostLikes(10L);

    verify(postLikePersistencePort).deleteByTarget(PostLikeTargetType.POST, 10L);
  }

  @Test
  @DisplayName("deleteAnswerLikes deletes answer likes by target")
  void deleteAnswerLikes_success() {
    postLikeCleanupService.deleteAnswerLikes(20L);

    verify(postLikePersistencePort).deleteByTarget(PostLikeTargetType.ANSWER, 20L);
  }
}
