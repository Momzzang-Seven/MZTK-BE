package momzzangseven.mztkbe.modules.auth.application.dto;

import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.modules.user.domain.model.User;

@Getter
@Builder
public class LoginResult {

  private String accessToken; // 아직 JWT 미연결이면 null 가능
  private String refreshToken; // 아직 미연결이면 null 가능
  private String grantType;
  private Integer expiresIn;

  private boolean isNewUser;
  private User user;

  public static LoginResult of(
      User user, boolean isNewUser, String accessToken, String refreshToken) {
    return LoginResult.builder()
        .user(user)
        .isNewUser(isNewUser)
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .grantType("Bearer")
        .expiresIn(1800)
        .build();
  }
}
