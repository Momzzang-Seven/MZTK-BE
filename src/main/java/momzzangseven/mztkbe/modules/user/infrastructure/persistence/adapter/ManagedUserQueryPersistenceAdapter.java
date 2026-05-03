package momzzangseven.mztkbe.modules.user.infrastructure.persistence.adapter;

import static momzzangseven.mztkbe.modules.user.infrastructure.persistence.entity.QUserEntity.userEntity;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.persistence.LikePatternEscaper;
import momzzangseven.mztkbe.modules.user.application.dto.GetManagedUsersPageQuery;
import momzzangseven.mztkbe.modules.user.application.dto.GetManagedUsersQuery;
import momzzangseven.mztkbe.modules.user.application.dto.ManagedUserView;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadManagedUsersPort;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** QueryDSL-backed managed-user profile adapter using only user-owned data. */
@Component
@RequiredArgsConstructor
public class ManagedUserQueryPersistenceAdapter implements LoadManagedUsersPort {

  private final JPAQueryFactory queryFactory;

  @Override
  public List<ManagedUserView> load(GetManagedUsersQuery query) {
    return queryFactory
        .select(
            Projections.constructor(
                ManagedUserView.class,
                userEntity.id,
                userEntity.nickname,
                userEntity.role,
                userEntity.email,
                userEntity.createdAt))
        .from(userEntity)
        .where(buildWhere(query))
        .fetch();
  }

  @Override
  public Page<ManagedUserView> loadPage(GetManagedUsersPageQuery query) {
    BooleanBuilder where = buildWhere(query);
    List<ManagedUserView> content =
        queryFactory
            .select(
                Projections.constructor(
                    ManagedUserView.class,
                    userEntity.id,
                    userEntity.nickname,
                    userEntity.role,
                    userEntity.email,
                    userEntity.createdAt))
            .from(userEntity)
            .where(where)
            .orderBy(orderSpecifiers(query.sortKey()))
            .offset((long) query.page() * query.size())
            .limit(query.size())
            .fetch();

    Long total =
        queryFactory.select(userEntity.id.count()).from(userEntity).where(where).fetchOne();
    return new PageImpl<>(
        content, PageRequest.of(query.page(), query.size()), total == null ? 0L : total);
  }

  private BooleanBuilder buildWhere(GetManagedUsersQuery query) {
    return buildWhere(query.search(), query.role(), query.candidateUserIds());
  }

  private BooleanBuilder buildWhere(GetManagedUsersPageQuery query) {
    return buildWhere(query.search(), query.role(), query.candidateUserIds());
  }

  private BooleanBuilder buildWhere(
      String search, UserRole role, java.util.Set<Long> candidateUserIds) {
    BooleanBuilder where = new BooleanBuilder();
    where.and(userEntity.role.in(UserRole.USER, UserRole.TRAINER));

    if (role != null) {
      where.and(userEntity.role.eq(role));
    }
    if (candidateUserIds != null) {
      if (candidateUserIds.isEmpty()) {
        where.and(userEntity.id.isNull());
      } else {
        where.and(userEntity.id.in(candidateUserIds));
      }
    }
    if (search != null) {
      String escaped = "%" + LikePatternEscaper.escape(search.toLowerCase()) + "%";
      where.and(
          userEntity
              .nickname
              .lower()
              .like(escaped, '!')
              .or(userEntity.email.lower().like(escaped, '!')));
    }
    return where;
  }

  private OrderSpecifier<?>[] orderSpecifiers(String sortKey) {
    OrderSpecifier<?> primary =
        switch (sortKey) {
          case "USER_ID" -> userEntity.id.desc();
          case "NICKNAME" -> userEntity.nickname.lower().asc().nullsLast();
          case "ROLE" -> userEntity.role.asc();
          case "JOINED_AT" -> userEntity.createdAt.desc();
          default -> throw new IllegalArgumentException("Unsupported sortKey: " + sortKey);
        };
    if ("USER_ID".equals(sortKey)) {
      return new OrderSpecifier<?>[] {primary};
    }
    return new OrderSpecifier<?>[] {primary, userEntity.id.desc()};
  }
}
