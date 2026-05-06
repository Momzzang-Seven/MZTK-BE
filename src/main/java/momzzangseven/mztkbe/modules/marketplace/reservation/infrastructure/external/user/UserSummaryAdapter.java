package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.user;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadUserSummaryPort;
import momzzangseven.mztkbe.modules.user.application.port.in.LoadUserInfoUseCase;
import org.springframework.stereotype.Component;

/**
 * Cross-module adapter that resolves user nickname data for the reservation module.
 *
 * <p>This is the only class in the {@code reservation} module allowed to import from the {@code
 * user} module. It calls {@link LoadUserInfoUseCase} — the public input port of the user module —
 * exclusively.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserSummaryAdapter implements LoadUserSummaryPort {

  private final LoadUserInfoUseCase loadUserInfoUseCase;

  @Override
  public Optional<UserSummary> findById(Long userId) {
    return loadUserInfoUseCase.loadUserById(userId).map(u -> new UserSummary(u.id(), u.nickname()));
  }

  /**
   * Batch-loads user summaries for multiple user IDs in a single query.
   *
   * <p>Duplicate IDs in the input list are collapsed before the query so that the same row is never
   * fetched more than once (e.g., when the same {@code trainerId} appears across multiple
   * reservations in a list response).
   *
   * @param userIds list of user IDs (may contain duplicates)
   * @return map of userId → UserSummary; absent key means the user was not found
   */
  @Override
  public Map<Long, UserSummary> findByIds(List<Long> userIds) {
    Collection<Long> uniqueIds = new HashSet<>(userIds);
    return loadUserInfoUseCase.loadUsersByIds(uniqueIds).stream()
        .collect(
            Collectors.toMap(u -> u.id(), u -> new UserSummary(u.id(), u.nickname()), (a, b) -> a));
  }
}
