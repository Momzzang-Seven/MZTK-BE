package momzzangseven.mztkbe.modules.post.infrastructure.persistence.adapter;

import static momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.QPostEntity.postEntity;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.persistence.LikePatternEscaper;
import momzzangseven.mztkbe.modules.post.application.dto.GetManagedBoardPostsPageQuery;
import momzzangseven.mztkbe.modules.post.application.dto.GetManagedBoardPostsQuery;
import momzzangseven.mztkbe.modules.post.application.dto.ManagedBoardPostTargetView;
import momzzangseven.mztkbe.modules.post.application.dto.ManagedBoardPostView;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadManagedBoardPostPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadManagedBoardPostsPort;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** QueryDSL-backed adapter for admin board post rows using only post-owned data. */
@Component
@RequiredArgsConstructor
public class ManagedBoardPostQueryPersistenceAdapter
    implements LoadManagedBoardPostsPort, LoadManagedBoardPostPort {

  private final JPAQueryFactory queryFactory;

  @Override
  public List<ManagedBoardPostView> load(GetManagedBoardPostsQuery query) {
    return queryFactory
        .select(
            Projections.constructor(
                ManagedBoardPostView.class,
                postEntity.id,
                postEntity.type,
                postEntity.status,
                postEntity.title,
                postEntity.content,
                postEntity.userId,
                postEntity.createdAt))
        .from(postEntity)
        .where(buildWhere(query))
        .fetch();
  }

  @Override
  public Page<ManagedBoardPostView> loadPage(GetManagedBoardPostsPageQuery query) {
    BooleanBuilder where = buildWhere(query.search(), query.status());
    List<ManagedBoardPostView> content =
        queryFactory
            .select(
                Projections.constructor(
                    ManagedBoardPostView.class,
                    postEntity.id,
                    postEntity.type,
                    postEntity.status,
                    postEntity.title,
                    postEntity.content,
                    postEntity.userId,
                    postEntity.createdAt))
            .from(postEntity)
            .where(where)
            .orderBy(orderSpecifiers(query.sortKey()))
            .offset((long) query.page() * query.size())
            .limit(query.size())
            .fetch();

    Long total =
        queryFactory.select(postEntity.id.count()).from(postEntity).where(where).fetchOne();
    return new PageImpl<>(
        content, PageRequest.of(query.page(), query.size()), total == null ? 0L : total);
  }

  @Override
  public Optional<ManagedBoardPostTargetView> load(Long postId) {
    ManagedBoardPostTargetView view =
        queryFactory
            .select(
                Projections.constructor(
                    ManagedBoardPostTargetView.class,
                    postEntity.id,
                    postEntity.type,
                    postEntity.status))
            .from(postEntity)
            .where(postEntity.id.eq(postId))
            .fetchOne();
    return Optional.ofNullable(view);
  }

  private BooleanBuilder buildWhere(GetManagedBoardPostsQuery query) {
    return buildWhere(query.search(), query.status());
  }

  private BooleanBuilder buildWhere(String search, PostStatus status) {
    BooleanBuilder where = new BooleanBuilder();
    if (status != null) {
      where.and(postEntity.status.eq(status));
    }
    if (search != null) {
      // TODO(MOM-242): Confirm whether admin board search should include writer fields.
      String escaped = "%" + LikePatternEscaper.escape(search.toLowerCase()) + "%";
      where.and(
          postEntity
              .title
              .lower()
              .like(escaped, '!')
              .or(postEntity.content.lower().like(escaped, '!')));
    }
    return where;
  }

  private OrderSpecifier<?>[] orderSpecifiers(String sortKey) {
    OrderSpecifier<?> primary =
        switch (sortKey) {
          case "CREATED_AT" -> postEntity.createdAt.desc();
          case "POST_ID" -> postEntity.id.desc();
          case "STATUS" -> postEntity.status.asc();
          case "TYPE" -> postEntity.type.asc();
          default -> throw new IllegalArgumentException("Unsupported sortKey: " + sortKey);
        };
    if ("POST_ID".equals(sortKey)) {
      return new OrderSpecifier<?>[] {primary};
    }
    return new OrderSpecifier<?>[] {primary, postEntity.id.desc()};
  }
}
