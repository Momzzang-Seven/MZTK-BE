package momzzangseven.mztkbe.modules.auth.infrastructure.kakao;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.modules.auth.application.dto.KakaoUserInfo;
import momzzangseven.mztkbe.modules.auth.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.auth.infrastructure.config.AuthWebClientProperties;
import momzzangseven.mztkbe.modules.auth.infrastructure.kakao.dto.KakaoTokenResponse;
import momzzangseven.mztkbe.modules.auth.infrastructure.kakao.dto.KakaoUserResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoApiAdapter implements KakaoAuthPort {

  private final KakaoAuthProperties props;
  private final WebClient webClient;
  private final AuthWebClientProperties authWebClientProperties;

  @Override
  public String getAccessToken(String authorizationCode) {
    try {
      MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
      form.add("grant_type", "authorization_code");
      form.add("client_id", props.getAuth().getClient());
      form.add("redirect_uri", props.getAuth().getRedirect());
      form.add("code", authorizationCode);

      KakaoTokenResponse token =
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
                                      "Kakao token request failed: status="
                                          + response.statusCode().value()
                                          + ", body="
                                          + body)))
              .bodyToMono(KakaoTokenResponse.class)
              .block(Duration.ofSeconds(authWebClientProperties.getBlockTimeoutSeconds()));

      if (token == null || token.getAccessToken() == null) {
        throw new BusinessException(
            ErrorCode.EXTERNAL_API_ERROR, "Failed to get Kakao access token");
      }

      return token.getAccessToken();
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Kakao token request failed", e);
    }
  }

  @Override
  public KakaoUserInfo getUserInfo(String accessToken) {
    try {
      KakaoUserResponse user =
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
                                      "Kakao userinfo request failed: status="
                                          + response.statusCode().value()
                                          + ", body="
                                          + body)))
              .bodyToMono(KakaoUserResponse.class)
              .block(Duration.ofSeconds(authWebClientProperties.getBlockTimeoutSeconds()));

      if (user == null) {
        throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Failed to get Kakao user info");
      }

      return KakaoUserInfo.builder()
          .providerUserId(String.valueOf(user.getId()))
          .email(user.getKakaoAccount() != null ? user.getKakaoAccount().getEmail() : null)
          .nickname(user.getProperties() != null ? user.getProperties().getNickname() : null)
          .profileImageUrl(
              user.getProperties() != null ? user.getProperties().getProfileImage() : null)
          .build();
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Kakao userinfo request failed", e);
    }
  }

  @Override
  public void unlinkUser(String providerUserId) {
    requireNonBlank(providerUserId, "providerUserId is required");
    requireNonBlank(props.getApi().getUnlinkUri(), "kakao.api.unlink-uri is required");
    requireNonBlank(props.getApi().getAdminKey(), "kakao.api.admin-key is required");

    try {
      MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
      form.add("target_id_type", "user_id");
      form.add("target_id", providerUserId);

      webClient
          .post()
          .uri(props.getApi().getUnlinkUri())
          .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + props.getApi().getAdminKey())
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
                                  "Kakao unlink request failed: status="
                                      + response.statusCode().value()
                                      + ", body="
                                      + body)))
          .toBodilessEntity()
          .block(Duration.ofSeconds(authWebClientProperties.getBlockTimeoutSeconds()));
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Kakao unlink request failed", e);
    }
  }

  private static void requireNonBlank(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, message);
    }
  }
}
