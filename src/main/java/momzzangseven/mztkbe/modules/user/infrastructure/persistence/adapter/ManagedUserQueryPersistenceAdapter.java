package momzzangseven.mztkbe.modules.user.infrastructure.persistence.adapter;

import static momzzangseven.mztkbe.modules.user.infrastructure.persistence.entity.QUserEntity.userEntity;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.persistence.LikePatternEscaper;
import momzzangseven.mztkbe.modules.user.application.dto.GetManagedUsersQuery;
import momzzangseven.mztkbe.modules.user.application.dto.ManagedUserView;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadManagedUsersPort;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
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

  private BooleanBuilder buildWhere(GetManagedUsersQuery query) {
    BooleanBuilder where = new BooleanBuilder();
    where.and(userEntity.role.in(UserRole.USER, UserRole.TRAINER));

    if (query.role() != null) {
      where.and(userEntity.role.eq(query.role()));
    }
    if (query.candidateUserIds() != null) {
      if (query.candidateUserIds().isEmpty()) {
        where.and(userEntity.id.isNull());
      } else {
        where.and(userEntity.id.in(query.candidateUserIds()));
      }
    }
    if (query.search() != null) {
      String escaped = "%" + LikePatternEscaper.escape(query.search().toLowerCase()) + "%";
      where.and(
          userEntity
              .nickname
              .lower()
              .like(escaped, '!')
              .or(userEntity.email.lower().like(escaped, '!')));
    }
    return where;
  }
}
