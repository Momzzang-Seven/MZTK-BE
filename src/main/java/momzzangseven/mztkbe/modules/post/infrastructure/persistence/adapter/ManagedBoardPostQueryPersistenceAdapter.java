package momzzangseven.mztkbe.modules.post.infrastructure.persistence.adapter;

import static momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.QPostEntity.postEntity;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.persistence.LikePatternEscaper;
import momzzangseven.mztkbe.modules.post.application.dto.GetManagedBoardPostsQuery;
import momzzangseven.mztkbe.modules.post.application.dto.ManagedBoardPostView;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadManagedBoardPostsPort;
import org.springframework.stereotype.Component;

/** QueryDSL-backed adapter for admin board post rows using only post-owned data. */
@Component
@RequiredArgsConstructor
public class ManagedBoardPostQueryPersistenceAdapter implements LoadManagedBoardPostsPort {

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

  private BooleanBuilder buildWhere(GetManagedBoardPostsQuery query) {
    BooleanBuilder where = new BooleanBuilder();
    if (query.status() != null) {
      where.and(postEntity.status.eq(query.status()));
    }
    if (query.search() != null) {
      // TODO(MOM-242): Confirm whether admin board search should include writer fields.
      String escaped = "%" + LikePatternEscaper.escape(query.search().toLowerCase()) + "%";
      where.and(
          postEntity
              .title
              .lower()
              .like(escaped, '!')
              .or(postEntity.content.lower().like(escaped, '!')));
    }
    return where;
  }
}
