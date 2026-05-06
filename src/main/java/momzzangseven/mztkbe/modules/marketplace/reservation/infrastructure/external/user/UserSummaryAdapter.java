package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.user;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

  @Override
  public Map<Long, UserSummary> findByIds(List<Long> userIds) {
    Map<Long, UserSummary> result = new HashMap<>();
    for (Long userId : userIds) {
      findById(userId)
          .ifPresentOrElse(
              summary -> result.put(userId, summary),
              () -> log.debug("No user summary found for userId={}", userId));
    }
    return result;
  }
}
