package momzzangseven.mztkbe.modules.user.domain.model;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;

@Getter
@Builder
public class User {

  private Long id;

  private AuthProvider provider;
  private String providerUserId;

  private String email;
  private String nickname;
  private String profileImageUrl;
  private String passwordHash;

  private String walletAddress;
  private UserRole role;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  // ✅ 이거 추가
  public static User socialUser(
      AuthProvider provider,
      String providerUserId,
      String email,
      String nickname,
      String profileImageUrl) {
    LocalDateTime now = LocalDateTime.now();

    return User.builder()
        .provider(provider)
        .providerUserId(providerUserId)
        .email(email)
        .nickname(nickname)
        .profileImageUrl(profileImageUrl)
        .role(UserRole.USER) // 기본 권한
        .walletAddress(null)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }
}
