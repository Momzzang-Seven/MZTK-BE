package momzzangseven.mztkbe.modules.post.infrastructure.persistence.adapter;

import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.post.domain.model.PostLike;
import momzzangseven.mztkbe.modules.post.domain.model.PostLikeTargetType;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository.PostLikeJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostLikePersistenceAdapter unit test")
class PostLikePersistenceAdapterTest {

  @Mock private PostLikeJpaRepository postLikeJpaRepository;

  @InjectMocks private PostLikePersistenceAdapter postLikePersistenceAdapter;

  @Test
  @DisplayName("insertIfAbsent delegates idempotent insert query")
  void insertIfAbsent_delegatesToRepository() {
    PostLike postLike = PostLike.create(PostLikeTargetType.POST, 10L, 7L);
    postLikePersistenceAdapter.insertIfAbsent(postLike);

    verify(postLikeJpaRepository)
        .insertIfAbsent(
            PostLikeTargetType.POST.name(), postLike.getTargetId(), postLike.getUserId());
  }
}
