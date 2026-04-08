package momzzangseven.mztkbe.modules.account.application.dto;

/**
 * Token data produced by {@code AuthTokenIssuer}.
 *
 * <p>Callers use this to assemble a {@link LoginResult}.
 */
public record IssuedTokens(
    String accessToken,
    String refreshToken,
    String grantType,
    Long accessTokenExpiresIn,
    Long refreshTokenExpiresIn) {}
