package momzzangseven.mztkbe.modules.auth.api.dto;

import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.modules.auth.application.dto.LoginResult;
import momzzangseven.mztkbe.modules.user.domain.model.User;

@Getter
@Builder
public class LoginResponse {

  private String accessToken;
  private String grantType;
  private Integer expiresIn;
  private boolean isNewUser;
  private UserInfo userInfo;

  public static LoginResponse from(LoginResult result) {
    User user = result.getUser();

    return LoginResponse.builder()
        .accessToken(result.getAccessToken())
        .grantType(result.getGrantType())
        .expiresIn(result.getExpiresIn())
        .isNewUser(result.isNewUser())
        .userInfo(UserInfo.from(user))
        .build();
  }

  @Getter
  @Builder
  public static class UserInfo {
    private Long userId;
    private String email;
    private String nickname;
    private String profileImageUrl;
    private String role;
    private String walletAddress;

    public static UserInfo from(User user) {
      return UserInfo.builder()
          .userId(user.getId())
          .email(user.getEmail())
          .nickname(user.getNickname())
          .profileImageUrl(user.getProfileImageUrl())
          .role(user.getRole().name())
          .walletAddress(user.getWalletAddress())
          .build();
    }
  }
}
