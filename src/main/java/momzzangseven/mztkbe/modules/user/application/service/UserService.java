package momzzangseven.mztkbe.modules.user.application.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.application.port.out.SaveUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

  private final LoadUserPort loadUserPort;
  private final SaveUserPort saveUserPort;

  @Transactional
  public SocialLoginOutcome loginOrRegisterSocial(
          AuthProvider provider,
          String providerUserId,
          String email,
          String nickname,
          String profileImageUrl) {

    if (provider == null) throw new IllegalArgumentException("provider is required");
    if (providerUserId == null || providerUserId.isBlank())
      throw new IllegalArgumentException("providerUserId is required");

    if (email == null || email.isBlank())
      throw new IllegalStateException("email is required for social login");

    // 1) provider + providerUserId 로 먼저 조회 (우리가 추가할 메서드)
    Optional<User> byProvider =
            loadUserPort.findByProviderAndProviderUserId(provider, providerUserId);

    if (byProvider.isPresent()) {
      return SocialLoginOutcome.existing(byProvider.get());
    }

    // 2) email 기준으로 기존 계정 있는지 확인
    Optional<User> byEmail = loadUserPort.loadUserByEmail(email);
    if (byEmail.isPresent()) {
      User existing = byEmail.get();

      // 계정 연동/통합 금지 정책
      if (existing.getAuthProvider() != provider) {
        throw new IllegalStateException(
                "Account already exists with a different provider. Email=" + email);
      }

      throw new IllegalStateException("Invalid social login state: providerUserId mismatch");
    }

    // 3) 완전 신규 생성
    User created =
            switch (provider) {
              case KAKAO -> User.createFromKakao(providerUserId, email, nickname, profileImageUrl);
              case GOOGLE -> User.createFromGoogle(providerUserId, email, nickname, profileImageUrl);
              default -> throw new IllegalArgumentException("Unsupported social provider: " + provider);
            };

    User saved = saveUserPort.saveUser(created);
    return SocialLoginOutcome.newUser(saved);
  }

  public record SocialLoginOutcome(User user, boolean isNewUser) {
    public static SocialLoginOutcome newUser(User user) {
      return new SocialLoginOutcome(user, true);
    }

    public static SocialLoginOutcome existing(User user) {
      return new SocialLoginOutcome(user, false);
    }
  }
}
