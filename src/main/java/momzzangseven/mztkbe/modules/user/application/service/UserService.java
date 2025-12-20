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

  /**
   * 소셜 로그인 진입점(= 로그인 or 자동 회원가입)
   *
   * <p>정책: - email 필수 - provider+providerUserId로 먼저 식별 - 없으면 email로 기존 계정 존재 여부 확인 - 존재하고 provider가
   * 다르면 "계정 통합/연동 금지"라면 예외 - 최종적으로 없으면 신규 생성
   */
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

    // 정책: email 필수
    if (email == null || email.isBlank())
      throw new IllegalStateException("email is required for social login");

    // 1) provider + providerUserId 로 먼저 조회
    Optional<User> byProvider =
        loadUserPort.findByProviderAndProviderUserId(provider, providerUserId);
    if (byProvider.isPresent()) {
      User existing = byProvider.get();

      //            // (선택) 프로필 최신화
      //            User updated = existing.updateSocialProfile(email, nickname, profileImageUrl);
      //            User saved = saveUserPort.save(updated);

      return SocialLoginOutcome.existing(existing);
    }

    // 2) email 기준으로 기존 계정이 있는지 확인
    Optional<User> byEmail = loadUserPort.findByEmail(email);
    if (byEmail.isPresent()) {
      User existing = byEmail.get();

      // 🔥 정책 선택: "계정 연동/통합 금지"라면 여기서 막는다
      // - 같은 email로 다른 provider로 로그인 시도하면 회원가입 차단
      if (existing.getProvider() != provider) {
        throw new IllegalStateException(
            "Account already exists with a different provider. Email=" + email);
      }

      throw new IllegalStateException("Invalid social login state: providerUserId mismatch");
    }

    // 3) 완전 신규 생성
    User created = User.socialUser(provider, providerUserId, email, nickname, profileImageUrl);
    User saved = saveUserPort.save(created);

    return SocialLoginOutcome.newUser(saved);
  }

  // ---- 결과 DTO(너 LoginService 코드와 호환되게 구성) ----
  public record SocialLoginOutcome(User user, boolean isNewUser) {
    public static SocialLoginOutcome newUser(User user) {
      return new SocialLoginOutcome(user, true);
    }

    public static SocialLoginOutcome existing(User user) {
      return new SocialLoginOutcome(user, false);
    }
  }
}
