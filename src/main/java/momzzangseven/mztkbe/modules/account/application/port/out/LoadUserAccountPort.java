package momzzangseven.mztkbe.modules.account.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.account.domain.model.UserAccount;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;

/** Output port for loading {@link UserAccount} records from persistence. */
public interface LoadUserAccountPort {

  /** Returns the account for the given user, regardless of status. */
  Optional<UserAccount> findByUserId(Long userId);

  /** Returns the account matching the given OAuth provider and provider-specific user ID. */
  Optional<UserAccount> findByProviderAndProviderUserId(
      AuthProvider provider, String providerUserId);

  /** Returns the ACTIVE account whose associated user has the given email address. */
  Optional<UserAccount> findActiveByEmail(String email);

  /** Returns the DELETED account whose associated user has the given email address. */
  Optional<UserAccount> findDeletedByEmail(String email);

  /**
   * Returns the DELETED account matching the given OAuth provider and provider-specific user ID.
   */
  Optional<UserAccount> findDeletedByProviderAndProviderUserId(
      AuthProvider provider, String providerUserId);

  /**
   * Returns user IDs of accounts that are DELETED and whose {@code deleted_at} is before {@code
   * cutoff}. Used by the hard-delete batch job.
   */
  List<Long> findUserIdsForHardDeletion(Instant cutoff, int limit);
}
