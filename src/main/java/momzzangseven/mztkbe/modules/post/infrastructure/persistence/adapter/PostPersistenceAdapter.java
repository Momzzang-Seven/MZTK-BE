package momzzangseven.mztkbe.modules.post.infrastructure.persistence.adapter;

import static momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.QPostEntity.postEntity;
import static momzzangseven.mztkbe.modules.tag.infrastructure.persistence.entity.QPostTagEntity.postTagEntity;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.global.persistence.LikePatternEscaper;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadPostPort;
import momzzangseven.mztkbe.modules.post.application.dto.PostCursorSearchCondition;
import momzzangseven.mztkbe.modules.post.application.dto.PostSearchCondition;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.PostEntity;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository.PostJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class PostPersistenceAdapter implements PostPersistencePort, LoadPostPort {

  private final PostJpaRepository postJpaRepository;
  private final JPAQueryFactory queryFactory;

  @Override
  public Post savePost(Post post) {
    PostEntity entity = PostEntity.fromDomain(post);
    PostEntity savedEntity = postJpaRepository.save(entity);
    return savedEntity.toDomain();
  }

  /**
   * 호출 트랜잭션의 읽기/쓰기 여부에 따라 락 전략을 자동으로 결정한다.
   *
   * <ul>
   *   <li>읽기 전용 트랜잭션 (readOnly=true, e.g. GetPostService) → 락 없이 조회
   *   <li>쓰기 트랜잭션 (e.g. PostProcessService) → PESSIMISTIC_WRITE 락을 걸고 조회
   * </ul>
   *
   * 이를 통해 서비스 레이어는 단일 {@code loadPost} 포트에 의존하면서도, 수정·삭제 시나리오에서 동시성 정합성을 보장한다.
   */
  @Override
  public Optional<Post> loadPost(Long postId) {
    boolean readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
    Optional<PostEntity> entityOpt =
        readOnly ? postJpaRepository.findById(postId) : postJpaRepository.findByIdForUpdate(postId);
    return entityOpt.map(entity -> entity.toDomain(Collections.emptyList()));
  }

  @Override
  public Optional<Post> loadPostForUpdate(Long postId) {
    return postJpaRepository
        .findByIdForUpdate(postId)
        .map(entity -> entity.toDomain(Collections.emptyList()));
  }

  @Override
  public void deletePost(Post post) {
    postJpaRepository.deleteById(post.getId());
    postJpaRepository.flush();
  }

  @Override
  public boolean existsPost(Long postId) {
    return postJpaRepository.existsById(postId);
  }

  @Override
  public List<Post> loadPostsByIdsPreservingOrder(List<Long> postIds) {
    if (postIds == null || postIds.isEmpty()) {
      return List.of();
    }
    Map<Long, PostEntity> entitiesById =
        postJpaRepository.findAllById(postIds).stream()
            .collect(Collectors.toMap(PostEntity::getId, Function.identity()));
    return postIds.stream()
        .map(entitiesById::get)
        .filter(java.util.Objects::nonNull)
        .map(PostEntity::toDomain)
        .toList();
  }

  @Override
  public List<Post> findPostsByCondition(
      PostSearchCondition condition, List<Long> filteredPostIds) {

    List<PostEntity> entities =
        queryFactory
            .selectFrom(postEntity)
            .where(
                eqType(condition.type()),
                containsSearch(condition.type(), condition.search()),
                filterByTagIds(filteredPostIds))
            .orderBy(postEntity.createdAt.desc())
            .offset((long) condition.page() * condition.size())
            .limit(fetchLimit(condition))
            .fetch();

    return entities.stream().map(PostEntity::toDomain).toList();
  }

  @Override
  public List<Post> findPostsByCursorCondition(PostCursorSearchCondition condition, Long tagId) {
    List<PostEntity> entities =
        tagId != null
            ? findPostsWithTagByCursor(condition, tagId)
            : queryFactory
                .selectFrom(postEntity)
                .where(
                    eqType(condition.type()),
                    containsCursorSearch(condition.type(), condition.search()),
                    cursorBefore(condition))
                .orderBy(postEntity.createdAt.desc(), postEntity.id.desc())
                .limit(condition.pageRequest().limitWithProbe())
                .fetch();

    return entities.stream().map(PostEntity::toDomain).toList();
  }

  @Override
  public List<Post> findPostsByAuthorCursor(
      Long authorId, PostType type, Long tagId, String search, CursorPageRequest pageRequest) {
    List<PostEntity> entities =
        hasAuthorSearch(type, search)
            ? findAuthorPostsBySearchCursor(authorId, type, tagId, search, pageRequest)
            : tagId != null
                ? findAuthorPostsWithTagByCursor(authorId, type, tagId, pageRequest)
                : findAuthorPostsByCursor(authorId, type, pageRequest);

    return entities.stream().map(PostEntity::toDomain).toList();
  }

  @Override
  public int markQuestionPostSolved(Long postId) {
    return postJpaRepository.markResolvedByIdIfType(
        postId, PostType.QUESTION, PostStatus.OPEN, PostStatus.RESOLVED);
  }

  // --- 동적 쿼리용 헬퍼 메서드 ---

  private BooleanExpression eqType(PostType type) {
    return type != null ? postEntity.type.eq(type) : null;
  }

  private BooleanExpression containsSearch(PostType type, String search) {
    if (!StringUtils.hasText(search)) return null;

    if (type == PostType.FREE) {
      return null;
    }
    return postEntity.title.contains(search);
  }

  private BooleanExpression containsCursorSearch(PostType type, String search) {
    if (!StringUtils.hasText(search)) return null;

    if (type == PostType.FREE) {
      return null;
    }
    BooleanExpression questionTitleMatches =
        postEntity.title.lower().like("%" + LikePatternEscaper.escape(search) + "%", '!');
    if (type == null) {
      return postEntity.type.eq(PostType.QUESTION).and(questionTitleMatches);
    }
    return questionTitleMatches;
  }

  private BooleanExpression filterByTagIds(List<Long> postIds) {
    // 1. 태그 검색 조건이 아예 없었음 (null) -> 필터링 안 함
    if (postIds == null) {
      return null;
    }

    // 2. 태그 검색은 했는데 결과가 없음 (Empty List) -> 게시글도 0건 나와야 함
    if (postIds.isEmpty()) {
      // ID가 -1인 게시글은 없을 테니 무조건 false 반환
      return postEntity.id.eq(-1L);
    }

    // 3. 태그 검색 결과가 있음 -> 해당 ID들 중에서 조회
    return postEntity.id.in(postIds);
  }

  private long fetchLimit(PostSearchCondition condition) {
    return (long) condition.size() + 1L;
  }

  private BooleanExpression cursorBefore(PostCursorSearchCondition condition) {
    return cursorBefore(condition.pageRequest());
  }

  private BooleanExpression cursorBefore(CursorPageRequest pageRequest) {
    if (!pageRequest.hasCursor()) {
      return null;
    }
    var cursor = pageRequest.cursor();
    return postEntity
        .createdAt
        .lt(cursor.createdAt())
        .or(postEntity.createdAt.eq(cursor.createdAt()).and(postEntity.id.lt(cursor.id())));
  }

  private List<PostEntity> findPostsWithTagByCursor(
      PostCursorSearchCondition condition, Long tagId) {
    String type = condition.type() == null ? null : condition.type().name();
    String escapedSearch = LikePatternEscaper.escape(condition.search());
    if (!condition.pageRequest().hasCursor()) {
      return postJpaRepository.findPostsByConditionWithTagFirstPageNative(
          type, escapedSearch, tagId, condition.pageRequest().limitWithProbe());
    }
    var cursor = condition.pageRequest().cursor();
    return postJpaRepository.findPostsByConditionWithTagAfterCursorNative(
        type,
        escapedSearch,
        tagId,
        cursor.createdAt(),
        cursor.id(),
        condition.pageRequest().limitWithProbe());
  }

  private List<PostEntity> findAuthorPostsBySearchCursor(
      Long authorId, PostType type, Long tagId, String search, CursorPageRequest pageRequest) {
    return queryFactory
        .selectFrom(postEntity)
        .where(
            postEntity.userId.eq(authorId),
            eqType(type),
            containsCursorSearch(type, search),
            hasTagId(tagId),
            cursorBefore(pageRequest))
        .orderBy(postEntity.createdAt.desc(), postEntity.id.desc())
        .limit(pageRequest.limitWithProbe())
        .fetch();
  }

  private List<PostEntity> findAuthorPostsWithTagByCursor(
      Long authorId, PostType type, Long tagId, CursorPageRequest pageRequest) {
    String typeName = type.name();
    if (!pageRequest.hasCursor()) {
      return postJpaRepository.findPostsByAuthorWithTagFirstPageNative(
          authorId, typeName, tagId, pageRequest.limitWithProbe());
    }
    var cursor = pageRequest.cursor();
    return postJpaRepository.findPostsByAuthorWithTagAfterCursorNative(
        authorId, typeName, tagId, cursor.createdAt(), cursor.id(), pageRequest.limitWithProbe());
  }

  private List<PostEntity> findAuthorPostsByCursor(
      Long authorId, PostType type, CursorPageRequest pageRequest) {
    String typeName = type.name();
    if (!pageRequest.hasCursor()) {
      return postJpaRepository.findPostsByAuthorFirstPageNative(
          authorId, typeName, pageRequest.limitWithProbe());
    }
    var cursor = pageRequest.cursor();
    return postJpaRepository.findPostsByAuthorAfterCursorNative(
        authorId, typeName, cursor.createdAt(), cursor.id(), pageRequest.limitWithProbe());
  }

  private BooleanExpression hasTagId(Long tagId) {
    if (tagId == null) {
      return null;
    }
    return JPAExpressions.selectOne()
        .from(postTagEntity)
        .where(postTagEntity.postId.eq(postEntity.id), postTagEntity.tagId.eq(tagId))
        .exists();
  }

  private boolean hasAuthorSearch(PostType type, String search) {
    return type != PostType.FREE && StringUtils.hasText(search);
  }
}
