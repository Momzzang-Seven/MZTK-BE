package momzzangseven.mztkbe.modules.post.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.post.domain.model.PostLike;
import momzzangseven.mztkbe.modules.post.domain.model.PostLikeTargetType;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.PostLikeEntity;
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
  @DisplayName("saveIfAbsent returns stored like after idempotent insert")
  void saveIfAbsent_returnsStoredLike_afterInsertIfAbsent() {
    PostLike postLike = PostLike.create(PostLikeTargetType.POST, 10L, 7L);
    PostLikeEntity existingEntity =
        PostLikeEntity.builder()
            .id(99L)
            .targetType(PostLikeTargetType.POST)
            .targetId(10L)
            .userId(7L)
            .build();
    setCreatedAt(existingEntity, LocalDateTime.of(2026, 4, 2, 10, 0));

    when(postLikeJpaRepository.insertIfAbsentReturning(PostLikeTargetType.POST.name(), 10L, 7L))
        .thenReturn(Optional.of(existingEntity));

    PostLike saved = postLikePersistenceAdapter.saveIfAbsent(postLike);

    assertThat(saved.getId()).isEqualTo(99L);
    assertThat(saved.getTargetType()).isEqualTo(PostLikeTargetType.POST);
    assertThat(saved.getTargetId()).isEqualTo(10L);
    assertThat(saved.getUserId()).isEqualTo(7L);
  }

  @Test
  @DisplayName("saveIfAbsent throws when row cannot be loaded after idempotent insert")
  void saveIfAbsent_throws_whenRowCannotBeLoaded() {
    PostLike postLike = PostLike.create(PostLikeTargetType.POST, 10L, 7L);

    when(postLikeJpaRepository.insertIfAbsentReturning(PostLikeTargetType.POST.name(), 10L, 7L))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> postLikePersistenceAdapter.saveIfAbsent(postLike))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to load post like after insert.");
  }

  private void setCreatedAt(PostLikeEntity entity, LocalDateTime createdAt) {
    try {
      java.lang.reflect.Field field = PostLikeEntity.class.getDeclaredField("createdAt");
      field.setAccessible(true);
      field.set(entity, createdAt);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }
}
