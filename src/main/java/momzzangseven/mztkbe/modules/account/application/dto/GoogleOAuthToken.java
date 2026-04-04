package momzzangseven.mztkbe.modules.account.application.dto;

/** Result of exchanging Google OAuth authorization code (access + optional refresh token). */
public record GoogleOAuthToken(String accessToken, String refreshToken) {

  public static GoogleOAuthToken of(String accessToken, String refreshToken) {
    return new GoogleOAuthToken(accessToken, refreshToken);
  }
}
