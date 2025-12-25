package momzzangseven.mztkbe.modules.auth.infrastructure.google;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.auth.application.dto.GoogleUserInfo;
import momzzangseven.mztkbe.modules.auth.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.auth.infrastructure.google.dto.GoogleTokenResponse;
import momzzangseven.mztkbe.modules.auth.infrastructure.google.dto.GoogleUserResponse;
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

    @Override
    public String getAccessToken(String authorizationCode) {

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
                        .bodyToMono(GoogleTokenResponse.class)
                        .block();

        if (token == null || token.getAccessToken() == null) {
            throw new IllegalStateException("Failed to get Google access token");
        }

        return token.getAccessToken();
    }

    @Override
    public GoogleUserInfo getUserInfo(String accessToken) {

        GoogleUserResponse user =
                webClient
                        .get()
                        .uri(props.getApi().getUserinfoUri())
                        .headers(h -> h.setBearerAuth(accessToken))
                        .retrieve()
                        .bodyToMono(GoogleUserResponse.class)
                        .block();

        if (user == null) {
            throw new IllegalStateException("Failed to get Google user info");
        }

        return GoogleUserInfo.builder()
                .providerUserId(user.getSub())
                .email(user.getEmail())
                .nickname(user.getName())
                .profileImageUrl(user.getPicture())
                .build();
    }
}
