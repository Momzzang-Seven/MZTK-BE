package momzzangseven.mztkbe.modules.comment.infrastructure.persistence.adapter;

import static momzzangseven.mztkbe.modules.comment.infrastructure.persistence.entity.QCommentEntity.commentEntity;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.persistence.LikePatternEscaper;
import momzzangseven.mztkbe.modules.comment.application.dto.GetManagedBoardCommentsQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.ManagedBoardCommentSearchView;
import momzzangseven.mztkbe.modules.comment.application.dto.ManagedBoardCommentTargetType;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadManagedBoardCommentsPort;
import momzzangseven.mztkbe.modules.comment.domain.model.CommentTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** QueryDSL-backed adapter for admin board global comment search rows. */
@Component
@RequiredArgsConstructor
public class ManagedBoardCommentQueryPersistenceAdapter implements LoadManagedBoardCommentsPort {

  private final JPAQueryFactory queryFactory;

  @Override
  public Page<ManagedBoardCommentSearchView> load(GetManagedBoardCommentsQuery query) {
    BooleanBuilder where = buildWhere(query);
    List<Tuple> rows =
        queryFactory
            .select(
                commentEntity.id,
                commentEntity.postId,
                commentEntity.answerId,
                commentEntity.parent.id,
                commentEntity.targetType,
                commentEntity.writerId,
                commentEntity.content,
                commentEntity.isDeleted,
                commentEntity.createdAt,
                commentEntity.updatedAt)
            .from(commentEntity)
            .where(where)
            .orderBy(orderSpecifiers(query.sortKey()))
            .offset((long) query.page() * query.size())
            .limit(query.size())
            .fetch();
    List<ManagedBoardCommentSearchView> content = rows.stream().map(this::toView).toList();

    Long total =
        queryFactory.select(commentEntity.id.count()).from(commentEntity).where(where).fetchOne();
    return new PageImpl<>(
        content, PageRequest.of(query.page(), query.size()), total == null ? 0L : total);
  }

  private BooleanBuilder buildWhere(GetManagedBoardCommentsQuery query) {
    BooleanBuilder where = new BooleanBuilder();
    if (query.commentId() != null) {
      where.and(commentEntity.id.eq(query.commentId()));
    }
    if (query.userId() != null) {
      where.and(commentEntity.writerId.eq(query.userId()));
    }
    if (query.targetType() != null) {
      where.and(commentEntity.targetType.eq(toDomainTargetType(query.targetType())));
    }
    if (query.search() != null) {
      String escaped = "%" + LikePatternEscaper.escape(query.search().toLowerCase()) + "%";
      where.and(commentEntity.content.lower().like(escaped, '!'));
    }
    return where;
  }

  private OrderSpecifier<?>[] orderSpecifiers(String sortKey) {
    OrderSpecifier<?> primary =
        switch (sortKey) {
          case "COMMENT_ID" -> commentEntity.id.desc();
          case "CREATED_AT" -> commentEntity.createdAt.desc();
          default -> throw new IllegalArgumentException("Unsupported sortKey: " + sortKey);
        };
    if ("COMMENT_ID".equals(sortKey)) {
      return new OrderSpecifier<?>[] {primary};
    }
    return new OrderSpecifier<?>[] {primary, commentEntity.id.desc()};
  }

  private ManagedBoardCommentSearchView toView(Tuple row) {
    return new ManagedBoardCommentSearchView(
        row.get(commentEntity.id),
        row.get(commentEntity.postId),
        row.get(commentEntity.answerId),
        row.get(commentEntity.parent.id),
        toManagedTargetType(row.get(commentEntity.targetType)),
        row.get(commentEntity.writerId),
        row.get(commentEntity.content),
        row.get(commentEntity.isDeleted),
        row.get(commentEntity.createdAt),
        row.get(commentEntity.updatedAt));
  }

  private CommentTargetType toDomainTargetType(ManagedBoardCommentTargetType targetType) {
    return switch (targetType) {
      case POST -> CommentTargetType.POST;
      case ANSWER -> CommentTargetType.ANSWER;
    };
  }

  private ManagedBoardCommentTargetType toManagedTargetType(CommentTargetType targetType) {
    return switch (targetType) {
      case POST -> ManagedBoardCommentTargetType.POST;
      case ANSWER -> ManagedBoardCommentTargetType.ANSWER;
    };
  }
}
