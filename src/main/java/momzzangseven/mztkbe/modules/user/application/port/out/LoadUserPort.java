package momzzangseven.mztkbe.modules.user.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.user.domain.model.User;

/**
 * Outbound Port for loading user data.
 *
 * <p>Hexagonal Architecture: - This is an OUTPUT PORT that defines operations needed by the
 * application layer - Implemented by an ADAPTER in the infrastructure layer - Allows the
 * application layer to remain independent of infrastructure detail
 */
public interface LoadUserPort {

  /**
   * Load user by email address.
   *
   * @param email user's email address
   * @return Optional containing User if found, empty otherwise
   */
  Optional<User> loadUserByEmail(String email);

  /**
   * Load user by Kakao ID.
   *
   * @param kakaoId Kakao unique identifier
   * @return Optional containing User if found, empty otherwise
   */
  Optional<User> loadUserByKakaoId(String kakaoId);

  /**
   * Load user by Google ID.
   *
   * @param googleId Google unique identifier
   * @return Optional containing User if found, empty otherwise
   */
  Optional<User> loadUserByGoogleId(String googleId);

  /**
   * Load user by wallet address.
   *
   * @param walletAddress blockchain wallet address
   * @return Optional containing User if found, empty otherwise
   */
  Optional<User> loadUserByWalletAddress(String walletAddress);

  /**
   * Load user by ID.
   *
   * @param id user's unique identifier
   * @return Optional containing User if found, empty otherwise
   */
  Optional<User> loadUserById(Long id);

  /**
   * Check if email already exists.
   *
   * @param email email address to check
   * @return true if email exists, false otherwise
   */
  boolean existsByEmail(String email);
}
