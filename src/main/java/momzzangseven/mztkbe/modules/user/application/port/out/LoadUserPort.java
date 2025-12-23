package momzzangseven.mztkbe.modules.user.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.domain.model.User;

public interface LoadUserPort {

    Optional<User> loadUserByEmail(String email);

    Optional<User> loadUserByKakaoId(String kakaoId);

    Optional<User> loadUserByGoogleId(String googleId);

    Optional<User> loadUserByWalletAddress(String walletAddress);

    Optional<User> loadUserById(Long id);

    boolean existsByEmail(String email);

    Optional<User> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
