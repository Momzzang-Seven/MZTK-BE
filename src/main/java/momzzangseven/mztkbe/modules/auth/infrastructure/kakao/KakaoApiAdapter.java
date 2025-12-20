package momzzangseven.mztkbe.modules.auth.infrastructure.kakao;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.auth.application.dto.KakaoUserInfo;
import momzzangseven.mztkbe.modules.auth.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.auth.infrastructure.kakao.dto.KakaoTokenResponse;
import momzzangseven.mztkbe.modules.auth.infrastructure.kakao.dto.KakaoUserResponse;
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

  @Override
  public KakaoUserInfo authenticate(String authorizationCode) {

    String redirectUri = props.getAuth().getRedirect();
    // 1️⃣ 토큰 요청
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "authorization_code");
    form.add("client_id", props.getAuth().getClient());
    form.add("redirect_uri", redirectUri);
    form.add("code", authorizationCode);

    KakaoTokenResponse token =
        webClient
            .post()
            .uri(props.getApi().getTokenUri())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .bodyValue(form)
            .retrieve()
            .onStatus(
                status -> status.is4xxClientError() || status.is5xxServerError(),
                response ->
                    response
                        .bodyToMono(String.class)
                        .flatMap(
                            body -> {
                              log.error("🔥 Kakao token request FAILED");
                              log.error("status={}", response.statusCode());
                              log.error("response body={}", body);
                              return reactor.core.publisher.Mono.error(
                                  new IllegalStateException("Kakao token error: " + body));
                            }))
            .bodyToMono(KakaoTokenResponse.class)
            .block();

    // 2️⃣ 사용자 정보 요청
    KakaoUserResponse user =
        webClient
            .get()
            .uri(props.getApi().getUserinfoUri())
            .headers(h -> h.setBearerAuth(token.getAccessToken()))
            .retrieve()
            .bodyToMono(KakaoUserResponse.class)
            .block();

    // 3️⃣ Application DTO로 변환
    return KakaoUserInfo.builder()
        .providerUserId(String.valueOf(user.getId()))
        .email(user.getKakaoAccount() != null ? user.getKakaoAccount().getEmail() : null)
        .nickname(user.getProperties() != null ? user.getProperties().getNickname() : null)
        .profileImageUrl(
            user.getProperties() != null ? user.getProperties().getProfileImage() : null)
        .build();
  }
}
