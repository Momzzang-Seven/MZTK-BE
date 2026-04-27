package momzzangseven.mztkbe.modules.post.infrastructure.persistence.adapter;

import static momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.QPostEntity.postEntity;
import static momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.QPostLikeEntity.postLikeEntity;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.global.pagination.KeysetCursor;
import momzzangseven.mztkbe.modules.post.application.port.out.LikedPostRow;
import momzzangseven.mztkbe.modules.post.application.port.out.PostLikePersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostLike;
import momzzangseven.mztkbe.modules.post.domain.model.PostLikeTargetType;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.PostLikeEntity;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository.PostLikeJpaRepository;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository.PostLikeJpaRepository.LikedPostProjection;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class PostLikePersistenceAdapter implements PostLikePersistencePort {

  private final PostLikeJpaRepository postLikeJpaRepository;
  private final JPAQueryFactory queryFactory;

  @Override
  public boolean exists(PostLikeTargetType targetType, Long targetId, Long userId) {
    return postLikeJpaRepository.existsByTargetTypeAndTargetIdAndUserId(
        targetType, targetId, userId);
  }

  @Override
  public PostLike save(PostLike postLike) {
    return toDomain(postLikeJpaRepository.save(toEntity(postLike)));
  }

  @Override
  public void insertIfAbsent(PostLike postLike) {
    postLikeJpaRepository.insertIfAbsent(
        postLike.getTargetType().name(), postLike.getTargetId(), postLike.getUserId());
  }

  @Override
  public void delete(PostLikeTargetType targetType, Long targetId, Long userId) {
    postLikeJpaRepository.deleteByTargetTypeAndTargetIdAndUserId(targetType, targetId, userId);
  }

  @Override
  public long countByTarget(PostLikeTargetType targetType, Long targetId) {
    return postLikeJpaRepository.countByTargetTypeAndTargetId(targetType, targetId);
  }

  @Override
  public Map<Long, Long> countByTargetIds(
      PostLikeTargetType targetType, Collection<Long> targetIds) {
    if (targetIds == null || targetIds.isEmpty()) {
      return Map.of();
    }
    return postLikeJpaRepository.countByTargetIds(targetType, targetIds).stream()
        .collect(
            Collectors.toMap(
                PostLikeJpaRepository.TargetLikeCountProjection::getTargetId,
                PostLikeJpaRepository.TargetLikeCountProjection::getLikeCount));
  }

  @Override
  public Set<Long> findLikedTargetIds(
      PostLikeTargetType targetType, Collection<Long> targetIds, Long userId) {
    if (targetIds == null || targetIds.isEmpty() || userId == null) {
      return Set.of();
    }
    return postLikeJpaRepository.findLikedTargetIds(targetType, targetIds, userId).stream()
        .collect(Collectors.toSet());
  }

  @Override
  public Optional<PostLike> find(PostLikeTargetType targetType, Long targetId, Long userId) {
    return postLikeJpaRepository
        .findByTargetTypeAndTargetIdAndUserId(targetType, targetId, userId)
        .map(this::toDomain);
  }

  @Override
  public void deleteByTarget(PostLikeTargetType targetType, Long targetId) {
    postLikeJpaRepository.deleteByTargetTypeAndTargetId(targetType, targetId);
  }

  @Override
  public List<LikedPostRow> findLikedPostsByCursor(
      Long userId, PostType type, String search, CursorPageRequest pageRequest) {
    if (hasLikedPostSearch(type, search)) {
      return findLikedPostsBySearchCursor(userId, type, search, pageRequest);
    }
    KeysetCursor cursor = pageRequest.cursor();
    List<LikedPostProjection> projections =
        cursor == null
            ? postLikeJpaRepository.findLikedPostsFirstPageNative(
                userId, type.name(), pageRequest.limitWithProbe())
            : postLikeJpaRepository.findLikedPostsAfterCursorNative(
                userId, type.name(), cursor.createdAt(), cursor.id(), pageRequest.limitWithProbe());
    return projections.stream().map(this::toLikedPostRow).toList();
  }

  private List<LikedPostRow> findLikedPostsBySearchCursor(
      Long userId, PostType type, String search, CursorPageRequest pageRequest) {
    return queryFactory
        .select(postLikeEntity, postEntity)
        .from(postLikeEntity, postEntity)
        .where(
            postLikeEntity.userId.eq(userId),
            postLikeEntity.targetType.eq(PostLikeTargetType.POST),
            postLikeEntity.targetId.eq(postEntity.id),
            postEntity.type.eq(type),
            postEntity.title.containsIgnoreCase(search),
            likedCursorBefore(pageRequest))
        .orderBy(postLikeEntity.createdAt.desc(), postLikeEntity.id.desc())
        .limit(pageRequest.limitWithProbe())
        .fetch()
        .stream()
        .map(this::toLikedPostRow)
        .toList();
  }

  private PostLikeEntity toEntity(PostLike postLike) {
    return PostLikeEntity.builder()
        .id(postLike.getId())
        .targetType(postLike.getTargetType())
        .targetId(postLike.getTargetId())
        .userId(postLike.getUserId())
        .build();
  }

  private PostLike toDomain(PostLikeEntity entity) {
    return PostLike.builder()
        .id(entity.getId())
        .targetType(entity.getTargetType())
        .targetId(entity.getTargetId())
        .userId(entity.getUserId())
        .createdAt(entity.getCreatedAt())
        .build();
  }

  private LikedPostRow toLikedPostRow(LikedPostProjection projection) {
    Post post =
        Post.builder()
            .id(projection.getPostId())
            .userId(projection.getUserId())
            .type(PostType.valueOf(projection.getType()))
            .title(projection.getTitle())
            .content(projection.getContent())
            .reward(projection.getReward())
            .acceptedAnswerId(projection.getAcceptedAnswerId())
            .status(PostStatus.valueOf(projection.getStatus()))
            .createdAt(projection.getPostCreatedAt())
            .updatedAt(projection.getPostUpdatedAt())
            .build();
    return new LikedPostRow(post, projection.getLikeId(), projection.getLikedAt());
  }

  private LikedPostRow toLikedPostRow(Tuple tuple) {
    PostLikeEntity likeEntity = tuple.get(postLikeEntity);
    Post post = tuple.get(postEntity).toDomain();
    return new LikedPostRow(post, likeEntity.getId(), likeEntity.getCreatedAt());
  }

  private BooleanExpression likedCursorBefore(CursorPageRequest pageRequest) {
    if (!pageRequest.hasCursor()) {
      return null;
    }
    KeysetCursor cursor = pageRequest.cursor();
    return postLikeEntity
        .createdAt
        .lt(cursor.createdAt())
        .or(postLikeEntity.createdAt.eq(cursor.createdAt()).and(postLikeEntity.id.lt(cursor.id())));
  }

  private boolean hasLikedPostSearch(PostType type, String search) {
    return type != PostType.FREE && StringUtils.hasText(search);
  }
}
