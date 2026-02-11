package momzzangseven.mztkbe.modules.auth.infrastructure.google;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.modules.auth.application.dto.GoogleOAuthToken;
import momzzangseven.mztkbe.modules.auth.application.dto.GoogleUserInfo;
import momzzangseven.mztkbe.modules.auth.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.auth.infrastructure.config.AuthWebClientProperties;
import momzzangseven.mztkbe.modules.auth.infrastructure.google.dto.GoogleTokenResponse;
import momzzangseven.mztkbe.modules.auth.infrastructure.google.dto.GoogleUserResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleApiAdapter implements GoogleAuthPort {

  private final GoogleAuthProperties props;
  private final WebClient webClient;
  private final AuthWebClientProperties authWebClientProperties;

  @Override
  public GoogleOAuthToken exchangeToken(String authorizationCode) {
    GoogleTokenResponse token = requestTokenResponse(authorizationCode);
    return GoogleOAuthToken.of(token.getAccessToken(), token.getRefreshToken());
  }

  @Override
  public String getAccessToken(String authorizationCode) {
    return requestTokenResponse(authorizationCode).getAccessToken();
  }

  private GoogleTokenResponse requestTokenResponse(String authorizationCode) {
    try {
      MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
      form.add("grant_type", "authorization_code");
      form.add("client_id", props.getOauth().getClientId());
      form.add("client_secret", props.getOauth().getClientSecret());
      form.add("redirect_uri", props.getOauth().getRedirectUri());
      form.add("code", authorizationCode);

      GoogleTokenResponse token =
          webClient
              .post()
              .uri(props.getApi().getTokenUri())
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .bodyValue(form)
              .retrieve()
              .onStatus(
                  HttpStatusCode::isError,
                  response ->
                      response
                          .bodyToMono(String.class)
                          .defaultIfEmpty("")
                          .map(
                              body ->
                                  new BusinessException(
                                      ErrorCode.EXTERNAL_API_ERROR,
                                      "Google token request failed: status="
                                          + response.statusCode().value()
                                          + ", body="
                                          + body)))
              .bodyToMono(GoogleTokenResponse.class)
              .block(Duration.ofSeconds(authWebClientProperties.getBlockTimeoutSeconds()));

      if (token == null || token.getAccessToken() == null) {
        throw new BusinessException(
            ErrorCode.EXTERNAL_API_ERROR, "Failed to get Google access token");
      }

      return token;
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Google token request failed", e);
    }
  }

  @Override
  public GoogleUserInfo getUserInfo(String accessToken) {
    try {
      GoogleUserResponse user =
          webClient
              .get()
              .uri(props.getApi().getUserinfoUri())
              .headers(h -> h.setBearerAuth(accessToken))
              .retrieve()
              .onStatus(
                  HttpStatusCode::isError,
                  response ->
                      response
                          .bodyToMono(String.class)
                          .defaultIfEmpty("")
                          .map(
                              body ->
                                  new BusinessException(
                                      ErrorCode.EXTERNAL_API_ERROR,
                                      "Google userinfo request failed: status="
                                          + response.statusCode().value()
                                          + ", body="
                                          + body)))
              .bodyToMono(GoogleUserResponse.class)
              .block(Duration.ofSeconds(authWebClientProperties.getBlockTimeoutSeconds()));

      if (user == null) {
        throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Failed to get Google user info");
      }

      return GoogleUserInfo.builder()
          .providerUserId(user.getSub())
          .email(user.getEmail())
          .nickname(user.getName())
          .profileImageUrl(user.getPicture())
          .build();
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException(
          ErrorCode.EXTERNAL_API_ERROR, "Google userinfo request failed", e);
    }
  }

  @Override
  public void revokeRefreshToken(String refreshToken) {
    requireNonBlank(refreshToken, "refreshToken is required");
    revokeToken(refreshToken);
  }

  @Override
  public void revokeAccessToken(String accessToken) {
    requireNonBlank(accessToken, "accessToken is required");
    revokeToken(accessToken);
  }

  private void revokeToken(String token) {
    requireNonBlank(props.getApi().getRevokeUri(), "google.api.revoke-uri is required");

    try {
      MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
      form.add("token", token);

      webClient
          .post()
          .uri(props.getApi().getRevokeUri())
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .bodyValue(form)
          .retrieve()
          .onStatus(
              HttpStatusCode::isError,
              response ->
                  response
                      .bodyToMono(String.class)
                      .defaultIfEmpty("")
                      .map(
                          body ->
                              new BusinessException(
                                  ErrorCode.EXTERNAL_API_ERROR,
                                  "Google revoke request failed: status="
                                      + response.statusCode().value()
                                      + ", body="
                                      + body)))
          .toBodilessEntity()
          .block(Duration.ofSeconds(authWebClientProperties.getBlockTimeoutSeconds()));
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Google revoke request failed", e);
    }
  }

  private static void requireNonBlank(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, message);
    }
  }
}
