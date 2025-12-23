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
    public String getAccessToken(String authorizationCode) {

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
                        .bodyToMono(KakaoTokenResponse.class)
                        .block();

        if (token == null || token.getAccessToken() == null) {
            throw new IllegalStateException("Failed to get Kakao access token");
        }

        return token.getAccessToken();
    }

    @Override
    public KakaoUserInfo getUserInfo(String accessToken) {

        KakaoUserResponse user =
                webClient
                        .get()
                        .uri(props.getApi().getUserinfoUri())
                        .headers(h -> h.setBearerAuth(accessToken))
                        .retrieve()
                        .bodyToMono(KakaoUserResponse.class)
                        .block();

        if (user == null) {
            throw new IllegalStateException("Failed to get Kakao user info");
        }

        return KakaoUserInfo.builder()
                .providerUserId(String.valueOf(user.getId()))
                .email(user.getKakaoAccount() != null ? user.getKakaoAccount().getEmail() : null)
                .nickname(user.getProperties() != null ? user.getProperties().getNickname() : null)
                .profileImageUrl(
                        user.getProperties() != null ? user.getProperties().getProfileImage() : null)
                .build();
    }
}
