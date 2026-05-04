package momzzangseven.mztkbe.modules.user.infrastructure.persistence.adapter;

import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserStatsPort;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository.UserStatsProjection;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository.UserStatsQueryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Persistence adapter for aggregated user statistics queries. */
@Component
@RequiredArgsConstructor
public class UserStatsPersistenceAdapter implements LoadUserStatsPort {

  private static final List<UserRole> COUNTED_ROLES = List.of(UserRole.USER, UserRole.TRAINER);

  private final UserStatsQueryRepository userStatsQueryRepository;

  @Override
  @Transactional(readOnly = true)
  public UserStatusCounts loadUserStatusCounts() {
    return new UserStatusCounts(
        userStatsQueryRepository.countUserAccountsByRoles(COUNTED_ROLES),
        userStatsQueryRepository.countUserAccountsByStatusAndRoles(
            AccountStatus.ACTIVE, COUNTED_ROLES),
        userStatsQueryRepository.countUserAccountsByStatusAndRoles(
            AccountStatus.BLOCKED, COUNTED_ROLES));
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserRoleCount> loadRoleCounts(Collection<UserRole> roles) {
    return userStatsQueryRepository.countUsersByRoles(roles).stream()
        .map(this::toRoleCount)
        .toList();
  }

  private UserRoleCount toRoleCount(UserStatsProjection projection) {
    return new UserRoleCount(projection.role(), projection.count());
  }
}
