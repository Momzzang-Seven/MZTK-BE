package momzzangseven.mztkbe.modules.post.infrastructure.persistence.adapter;

import static momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.QPostEntity.postEntity;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadPostPort;
import momzzangseven.mztkbe.modules.post.application.dto.PostSearchCondition;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.PostEntity;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository.PostJpaRepository;
import org.springframework.stereotype.Component;
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

  @Override
  public Optional<Post> loadPost(Long postId) {
    PostEntity entity = postJpaRepository.findById(postId).orElse(null);
    if (entity == null) return Optional.empty();

    return Optional.of(entity.toDomain(Collections.emptyList()));
  }

  @Override
  public void deletePost(Post post) {
    postJpaRepository.delete(PostEntity.fromDomain(post));
  }

  @Override
  public boolean existsPost(Long postId) {
    return postJpaRepository.existsById(postId);
  }

  @Override
  public List<Post> findPostsByCondition(
      PostSearchCondition condition, List<Long> filteredPostIds) {

    List<PostEntity> entities =
        queryFactory
            .selectFrom(postEntity)
            .where(
                eqType(condition.getType()),
                containsSearch(condition.getSearch()),
                filterByTagIds(filteredPostIds))
            .orderBy(postEntity.createdAt.desc())
            .offset((long) condition.getPage() * condition.getSize())
            .limit(condition.getSize())
            .fetch();

    return entities.stream().map(PostEntity::toDomain).toList();
  }

  // --- 동적 쿼리용 헬퍼 메서드 ---

  private BooleanExpression eqType(PostType type) {
    return type != null ? postEntity.type.eq(type) : null;
  }

  private BooleanExpression containsSearch(String search) {
    if (!StringUtils.hasText(search)) return null;
    return postEntity.title.contains(search).or(postEntity.content.contains(search));
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
}
