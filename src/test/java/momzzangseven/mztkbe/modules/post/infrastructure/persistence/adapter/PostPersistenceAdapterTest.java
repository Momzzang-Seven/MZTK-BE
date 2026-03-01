package momzzangseven.mztkbe.modules.post.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.PostEntity;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository.PostJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostPersistenceAdapter unit test")
class PostPersistenceAdapterTest {

  @Mock private PostJpaRepository postJpaRepository;
  @Mock private JPAQueryFactory queryFactory;

  @InjectMocks private PostPersistenceAdapter postPersistenceAdapter;

  @Test
  @DisplayName("savePost maps domain to entity and returns mapped domain")
  void savePostMapsDomain() {
    Post post =
        Post.builder()
            .id(null)
            .userId(3L)
            .type(PostType.FREE)
            .title("title")
            .content("content")
            .imageUrls(List.of("img1"))
            .reward(0L)
            .isSolved(false)
            .build();

    PostEntity savedEntity =
        PostEntity.builder()
            .id(100L)
            .userId(3L)
            .type(PostType.FREE)
            .title("title")
            .content("content")
            .imageUrls(List.of("img1"))
            .reward(0L)
            .isSolved(false)
            .build();

    when(postJpaRepository.save(any(PostEntity.class))).thenReturn(savedEntity);

    Post result = postPersistenceAdapter.savePost(post);

    ArgumentCaptor<PostEntity> captor = ArgumentCaptor.forClass(PostEntity.class);
    verify(postJpaRepository).save(captor.capture());

    PostEntity mapped = captor.getValue();
    assertThat(mapped.getUserId()).isEqualTo(3L);
    assertThat(mapped.getType()).isEqualTo(PostType.FREE);
    assertThat(mapped.getTitle()).isEqualTo("title");
    assertThat(mapped.getContent()).isEqualTo("content");
    assertThat(mapped.getImageUrls()).containsExactly("img1");

    assertThat(result.getId()).isEqualTo(100L);
    assertThat(result.getUserId()).isEqualTo(3L);
    assertThat(result.getType()).isEqualTo(PostType.FREE);
    assertThat(result.getTags()).isEmpty();
  }

  @Test
  @DisplayName("loadPost returns empty when missing")
  void loadPostReturnsEmptyWhenMissing() {
    when(postJpaRepository.findById(999L)).thenReturn(Optional.empty());

    Optional<Post> result = postPersistenceAdapter.loadPost(999L);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("loadPost maps found entity with empty tags")
  void loadPostMapsFoundEntity() {
    PostEntity entity =
        PostEntity.builder()
            .id(10L)
            .userId(4L)
            .type(PostType.QUESTION)
            .title("question")
            .content("body")
            .imageUrls(List.of("img"))
            .reward(50L)
            .isSolved(false)
            .build();

    when(postJpaRepository.findById(10L)).thenReturn(Optional.of(entity));

    Optional<Post> result = postPersistenceAdapter.loadPost(10L);

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().getId()).isEqualTo(10L);
    assertThat(result.orElseThrow().getType()).isEqualTo(PostType.QUESTION);
    assertThat(result.orElseThrow().getTags()).isEmpty();
  }

  @Test
  @DisplayName("deletePost removes row and flushes")
  void deletePostDelegatesToRepository() {
    Post post =
        Post.builder()
            .id(77L)
            .userId(1L)
            .type(PostType.FREE)
            .title("t")
            .content("c")
            .reward(0L)
            .isSolved(false)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    postPersistenceAdapter.deletePost(post);

    verify(postJpaRepository).deleteById(77L);
    verify(postJpaRepository).flush();
  }

  @Test
  @DisplayName("existsPost delegates to repository")
  void existsPostDelegates() {
    when(postJpaRepository.existsById(5L)).thenReturn(true);

    boolean exists = postPersistenceAdapter.existsPost(5L);

    assertThat(exists).isTrue();
    verify(postJpaRepository).existsById(5L);
  }

  @Test
  @DisplayName("markQuestionPostSolved enforces QUESTION type in repository call")
  void markQuestionPostSolvedDelegates() {
    when(postJpaRepository.markSolvedByIdIfType(9L, PostType.QUESTION)).thenReturn(1);

    int updated = postPersistenceAdapter.markQuestionPostSolved(9L);

    assertThat(updated).isEqualTo(1);
    verify(postJpaRepository).markSolvedByIdIfType(9L, PostType.QUESTION);
  }
}
