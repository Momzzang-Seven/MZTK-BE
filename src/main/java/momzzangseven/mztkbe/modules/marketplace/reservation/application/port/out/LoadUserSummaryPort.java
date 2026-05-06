package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Output port for fetching user nickname data needed to enrich reservation responses.
 *
 * <p>Implemented by {@code UserSummaryAdapter} in the reservation module's external layer. The
 * adapter is the only place allowed to cross the module boundary into the {@code user} module.
 */
public interface LoadUserSummaryPort {

  /**
   * Summary of a user required for reservation display.
   *
   * @param userId user's primary key
   * @param nickname user's display nickname
   */
  record UserSummary(Long userId, String nickname) {}

  /**
   * Find a single user summary by user ID.
   *
   * @param userId user ID
   * @return Optional containing the summary if the user is found
   */
  Optional<UserSummary> findById(Long userId);

  /**
   * Batch-load user summaries for multiple user IDs. Used in list endpoints to avoid N+1 calls.
   *
   * @param userIds list of user IDs
   * @return map of userId → UserSummary; absent key means the user was not found
   */
  Map<Long, UserSummary> findByIds(List<Long> userIds);
}
