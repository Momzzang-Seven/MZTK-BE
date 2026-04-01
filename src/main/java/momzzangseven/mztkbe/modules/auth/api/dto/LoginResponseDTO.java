package momzzangseven.mztkbe.modules.auth.api.dto;

import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.modules.auth.application.dto.LoginResult;
import momzzangseven.mztkbe.modules.user.domain.model.User;

/**
 * Login response DTO to client.
 *
 * <p>This DTO is the ACTUAL DATA that goes inside ApiResponse.
 *
 * <p>Final JSON structure: { "status": "SUCCESS", "data": { ← THIS IS LoginResponseDTO
 * "accessToken": "...", "grantType": "Bearer", ... } }
 */
@Getter
@Builder
public class LoginResponseDTO {
  private String accessToken;

  private String grantType;

  private Long expiresIn;

  private Boolean isNewUser;

  private UserInfo userInfo;

  /**
   * Convert application layer result into API response DTO.
   *
   * @param result login result from service
   * @return response DTO sent to client
   */
  public static LoginResponseDTO from(LoginResult result) {
    return LoginResponseDTO.builder()
        .accessToken(result.accessToken())
        .grantType(result.grantType())
        .expiresIn(result.accessTokenExpiresIn())
        .isNewUser(result.isNewUser())
        .userInfo(UserInfo.from(result.user(), result.walletAddress()))
        .build();
  }

  /** Nested DTO for user information. */
  @Getter
  @Builder
  public static class UserInfo {

    private Long userId;

    private String email;

    private String nickname;

    private String profileImage;

    private String role;

    private String walletAddress;

    /**
     * Map the User domain entity to API payload.
     *
     * @param user domain user
     * @param walletAddress active wallet address from user_wallets, or null
     * @return nested user info DTO
     */
    public static UserInfo from(User user, String walletAddress) {
      return UserInfo.builder()
          .userId(user.getId())
          .email(user.getEmail())
          .nickname(user.getNickname())
          .profileImage(user.getProfileImageUrl())
          .role(user.getRole().name())
          .walletAddress(walletAddress)
          .build();
    }
  }
}
