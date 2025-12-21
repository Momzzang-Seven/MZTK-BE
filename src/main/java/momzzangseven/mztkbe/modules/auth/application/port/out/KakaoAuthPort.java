package momzzangseven.mztkbe.modules.auth.application.port.out;

import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticationContext;
import momzzangseven.mztkbe.modules.auth.application.dto.KakaoUserInfo;

/**
 * Outbound port for Kakao authentication operations.
 *
 * This interface abstracts Kakao OAuth API calls.
 * Implemented by infrastructure layer (KakaoApiAdapter).
 */
 public interface KakaoAuthPort {

    /**
     * Exchange authorization code for access token.
     *
     * @param authorizationCode Authorization code from Kakao OAuth callback
     * @return Kakao access token
     * @throws ExternalApiException if token exchange fails
     */
    String getAccessToken(String authorizationCode);

    /**
     * Get user information from Kakao using access token.
     *
     * @param accessToken Kakao access token
     * @return Kakao user information (kakaoId, email, nickname, profile image)
     * @throws ExternalApiException if user info retrieval fails
     */
    KakaoUserInfo getUserInfo(String accessToken);
 }
