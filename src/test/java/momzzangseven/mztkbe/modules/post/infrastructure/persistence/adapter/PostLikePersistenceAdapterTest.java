package momzzangseven.mztkbe.modules.post.infrastructure.persistence.adapter;

import static momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.QPostEntity.postEntity;
import static momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.QPostLikeEntity.postLikeEntity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.global.pagination.CursorCodec;
import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.global.pagination.CursorScope;
import momzzangseven.mztkbe.global.pagination.KeysetCursor;
import momzzangseven.mztkbe.modules.post.application.port.out.LikedPostRow;
import momzzangseven.mztkbe.modules.post.domain.model.PostLike;
import momzzangseven.mztkbe.modules.post.domain.model.PostLikeTargetType;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository.PostLikeJpaRepository;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository.PostLikeJpaRepository.LikedPostProjection;
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
  @Mock private JPAQueryFactory queryFactory;
  @Mock private JPAQuery<Tuple> jpaQuery;

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

  @Test
  @DisplayName("findLikedPostsByCursor delegates first page query and maps projection")
  void findLikedPostsByCursor_firstPage() {
    LocalDateTime likedAt = LocalDateTime.of(2026, 4, 26, 12, 0);
    LocalDateTime postCreatedAt = LocalDateTime.of(2026, 4, 20, 9, 0);
    CursorPageRequest pageRequest =
        CursorPageRequest.of(null, 10, 10, 50, CursorScope.likedPosts(7L, "FREE"));
    LikedPostProjection projection =
        projection(100L, likedAt, 10L, 3L, PostType.FREE, null, 0L, PostStatus.OPEN, postCreatedAt);
    when(postLikeJpaRepository.findLikedPostsFirstPageNative(7L, "FREE", 11))
        .thenReturn(List.of(projection));

    List<LikedPostRow> rows =
        postLikePersistenceAdapter.findLikedPostsByCursor(7L, PostType.FREE, null, pageRequest);

    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().likeId()).isEqualTo(100L);
    assertThat(rows.getFirst().likedAt()).isEqualTo(likedAt);
    assertThat(rows.getFirst().post().getId()).isEqualTo(10L);
    assertThat(rows.getFirst().post().getType()).isEqualTo(PostType.FREE);
    verify(postLikeJpaRepository).findLikedPostsFirstPageNative(7L, "FREE", 11);
  }

  @Test
  @DisplayName("findLikedPostsByCursor delegates next page query using cursor")
  void findLikedPostsByCursor_nextPage() {
    LocalDateTime cursorLikedAt = LocalDateTime.of(2026, 4, 26, 12, 0);
    String scope = CursorScope.likedPosts(7L, "QUESTION");
    String cursor = CursorCodec.encode(new KeysetCursor(cursorLikedAt, 100L, scope));
    CursorPageRequest pageRequest = CursorPageRequest.of(cursor, 5, 10, 50, scope);
    when(postLikeJpaRepository.findLikedPostsAfterCursorNative(
            7L, "QUESTION", cursorLikedAt, 100L, 6))
        .thenReturn(List.of());

    List<LikedPostRow> rows =
        postLikePersistenceAdapter.findLikedPostsByCursor(7L, PostType.QUESTION, null, pageRequest);

    assertThat(rows).isEmpty();
    verify(postLikeJpaRepository)
        .findLikedPostsAfterCursorNative(7L, "QUESTION", cursorLikedAt, 100L, 6);
  }

  @Test
  @DisplayName("findLikedPostsByCursor uses QueryDSL when search exists")
  void findLikedPostsByCursor_searchUsesQueryDsl() {
    CursorPageRequest pageRequest =
        CursorPageRequest.of(null, 10, 10, 50, CursorScope.likedPosts(7L, "QUESTION", "form"));
    when(queryFactory.select(postLikeEntity, postEntity)).thenReturn(jpaQuery);
    when(jpaQuery.from(postLikeEntity, postEntity)).thenReturn(jpaQuery);
    when(jpaQuery.where(any(Predicate[].class))).thenReturn(jpaQuery);
    when(jpaQuery.orderBy(any(OrderSpecifier[].class))).thenReturn(jpaQuery);
    when(jpaQuery.limit(11L)).thenReturn(jpaQuery);
    when(jpaQuery.fetch()).thenReturn(List.of());

    List<LikedPostRow> rows =
        postLikePersistenceAdapter.findLikedPostsByCursor(
            7L, PostType.QUESTION, "form", pageRequest);

    assertThat(rows).isEmpty();
    verify(queryFactory).select(postLikeEntity, postEntity);
    verify(jpaQuery).where(any(Predicate[].class));
    verifyNoInteractions(postLikeJpaRepository);
  }

  private LikedPostProjection projection(
      Long likeId,
      LocalDateTime likedAt,
      Long postId,
      Long userId,
      PostType type,
      Long acceptedAnswerId,
      Long reward,
      PostStatus status,
      LocalDateTime postCreatedAt) {
    LikedPostProjection projection = org.mockito.Mockito.mock(LikedPostProjection.class);
    when(projection.getLikeId()).thenReturn(likeId);
    when(projection.getLikedAt()).thenReturn(likedAt);
    when(projection.getPostId()).thenReturn(postId);
    when(projection.getUserId()).thenReturn(userId);
    when(projection.getType()).thenReturn(type.name());
    when(projection.getTitle()).thenReturn(type == PostType.QUESTION ? "question" : null);
    when(projection.getContent()).thenReturn("content");
    when(projection.getReward()).thenReturn(reward);
    when(projection.getAcceptedAnswerId()).thenReturn(acceptedAnswerId);
    when(projection.getStatus()).thenReturn(status.name());
    when(projection.getPostCreatedAt()).thenReturn(postCreatedAt);
    when(projection.getPostUpdatedAt()).thenReturn(postCreatedAt);
    return projection;
  }
}
