package momzzangseven.mztkbe.modules.user.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.domain.model.User;

public interface LoadUserPort {

  Optional<User> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

  // ✅ 이 줄 추가 (에러 해결 포인트)
  Optional<User> findByEmail(String email);
}
