package momzzangseven.mztkbe.modules.account.infrastructure.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.modules.account.application.dto.GoogleOAuthToken;
import momzzangseven.mztkbe.modules.account.application.dto.GoogleUserInfo;
import momzzangseven.mztkbe.modules.account.infrastructure.config.AuthWebClientProperties;
import momzzangseven.mztkbe.modules.account.infrastructure.google.dto.GoogleTokenResponse;
import momzzangseven.mztkbe.modules.account.infrastructure.google.dto.GoogleUserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@SuppressWarnings("unchecked")
class GoogleApiAdapterTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private WebClient webClient;
  private GoogleApiAdapter adapter;
  private GoogleAuthProperties properties;
  private AuthWebClientProperties webClientProperties;

  @BeforeEach
  void setUp() {
    webClient = mock(WebClient.class);
    properties = new GoogleAuthProperties();
    properties.getOauth().setClientId("google-client");
    properties.getOauth().setClientSecret("google-secret");
    properties.getOauth().setRedirectUri("http://localhost/google-callback");
    properties.getApi().setTokenUri("https://google/token");
    properties.getApi().setUserinfoUri("https://google/userinfo");
    properties.getApi().setRevokeUri("https://google/revoke");

    webClientProperties = new AuthWebClientProperties();
    webClientProperties.setBlockTimeoutSeconds(1);

    adapter = new GoogleApiAdapter(properties, webClient, webClientProperties);
  }

  @Test
  void exchangeToken_returnsOAuthToken_whenResponseValid() throws Exception {
    WebClient.RequestBodyUriSpec postSpec = mock(WebClient.RequestBodyUriSpec.class);
    WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
    WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
    WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

    GoogleTokenResponse tokenResponse =
        objectMapper.readValue(
            """
            {"access_token":"access-token","refresh_token":"refresh-token"}
            """,
            GoogleTokenResponse.class);

    when(webClient.post()).thenReturn(postSpec);
    when(postSpec.uri("https://google/token")).thenReturn(bodySpec);
    when(bodySpec.contentType(MediaType.APPLICATION_FORM_URLENCODED)).thenReturn(bodySpec);
    when(bodySpec.bodyValue(any())).thenReturn(headersSpec);
    when(headersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    when(responseSpec.bodyToMono(GoogleTokenResponse.class)).thenReturn(Mono.just(tokenResponse));

    GoogleOAuthToken token = adapter.exchangeToken("auth-code");

    assertThat(token.accessToken()).isEqualTo("access-token");
    assertThat(token.refreshToken()).isEqualTo("refresh-token");
  }

  @Test
  void getAccessToken_throws_whenTokenResponseMissingAccessToken() {
    WebClient.RequestBodyUriSpec postSpec = mock(WebClient.RequestBodyUriSpec.class);
    WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
    WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
    WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

    when(webClient.post()).thenReturn(postSpec);
    when(postSpec.uri("https://google/token")).thenReturn(bodySpec);
    when(bodySpec.contentType(MediaType.APPLICATION_FORM_URLENCODED)).thenReturn(bodySpec);
    when(bodySpec.bodyValue(any())).thenReturn(headersSpec);
    when(headersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    when(responseSpec.bodyToMono(GoogleTokenResponse.class)).thenReturn(Mono.empty());

    assertThatThrownBy(() -> adapter.getAccessToken("auth-code"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Failed to get Google access token");
  }

  @Test
  void getAccessToken_throws_whenTokenResponseAccessTokenIsNull() {
    WebClient.RequestBodyUriSpec postSpec = mock(WebClient.RequestBodyUriSpec.class);
    WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
    WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
    WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
    GoogleTokenResponse tokenResponse = mock(GoogleTokenResponse.class);

    when(tokenResponse.getAccessToken()).thenReturn(null);
    when(webClient.post()).thenReturn(postSpec);
    when(postSpec.uri("https://google/token")).thenReturn(bodySpec);
    when(bodySpec.contentType(MediaType.APPLICATION_FORM_URLENCODED)).thenReturn(bodySpec);
    when(bodySpec.bodyValue(any())).thenReturn(headersSpec);
    when(headersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    when(responseSpec.bodyToMono(GoogleTokenResponse.class)).thenReturn(Mono.just(tokenResponse));

    assertThatThrownBy(() -> adapter.getAccessToken("auth-code"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Failed to get Google access token");
  }

  @Test
  void getUserInfo_returnsMappedUserInfo_whenResponseValid() throws Exception {
    WebClient.RequestHeadersUriSpec getSpec = mock(WebClient.RequestHeadersUriSpec.class);
    WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
    WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

    GoogleUserResponse userResponse =
        objectMapper.readValue(
            """
            {"sub":"provider-id","email":"user@example.com","name":"User","picture":"http://img"}
            """,
            GoogleUserResponse.class);

    when(webClient.get()).thenReturn(getSpec);
    when(getSpec.uri("https://google/userinfo")).thenReturn(headersSpec);
    when(headersSpec.headers(any())).thenReturn(headersSpec);
    when(headersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    when(responseSpec.bodyToMono(GoogleUserResponse.class)).thenReturn(Mono.just(userResponse));

    GoogleUserInfo userInfo = adapter.getUserInfo("access-token");

    assertThat(userInfo.getProviderUserId()).isEqualTo("provider-id");
    assertThat(userInfo.getEmail()).isEqualTo("user@example.com");
    assertThat(userInfo.getNickname()).isEqualTo("User");
    assertThat(userInfo.getProfileImageUrl()).isEqualTo("http://img");
  }

  @Test
  void getUserInfo_throws_whenResponseBodyNull() {
    WebClient.RequestHeadersUriSpec getSpec = mock(WebClient.RequestHeadersUriSpec.class);
    WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
    WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

    when(webClient.get()).thenReturn(getSpec);
    when(getSpec.uri("https://google/userinfo")).thenReturn(headersSpec);
    when(headersSpec.headers(any())).thenReturn(headersSpec);
    when(headersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    when(responseSpec.bodyToMono(GoogleUserResponse.class)).thenReturn(Mono.empty());

    assertThatThrownBy(() -> adapter.getUserInfo("access-token"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Failed to get Google user info");
  }

  @Test
  void getUserInfo_rethrowsBusinessException_withoutWrapping() {
    BusinessException expected =
        new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "already business exception");
    when(webClient.get()).thenThrow(expected);

    assertThatThrownBy(() -> adapter.getUserInfo("access-token")).isSameAs(expected);
  }

  @Test
  void revokeRefreshToken_throws_whenTokenBlank() {
    assertThatThrownBy(() -> adapter.revokeRefreshToken(" "))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("refreshToken is required");
  }

  @Test
  void revokeRefreshToken_throws_whenTokenNull() {
    assertThatThrownBy(() -> adapter.revokeRefreshToken(null))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("refreshToken is required");
  }

  @Test
  void revokeAccessToken_throws_whenRevokeUriMissing() {
    properties.getApi().setRevokeUri(" ");

    assertThatThrownBy(() -> adapter.revokeAccessToken("token"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("google.api.revoke-uri is required");
  }

  @Test
  void revokeAccessToken_callsRevokeApi_whenInputValid() {
    WebClient.RequestBodyUriSpec postSpec = mock(WebClient.RequestBodyUriSpec.class);
    WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
    WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
    WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

    when(webClient.post()).thenReturn(postSpec);
    when(postSpec.uri("https://google/revoke")).thenReturn(bodySpec);
    when(bodySpec.contentType(MediaType.APPLICATION_FORM_URLENCODED)).thenReturn(bodySpec);
    when(bodySpec.bodyValue(any())).thenReturn(headersSpec);
    when(headersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    when(responseSpec.toBodilessEntity()).thenReturn(Mono.just(ResponseEntity.ok().build()));

    adapter.revokeAccessToken("access-token");

    verify(bodySpec).contentType(MediaType.APPLICATION_FORM_URLENCODED);
  }

  @Test
  void revokeAccessToken_wrapsUnexpectedException() {
    WebClient.RequestBodyUriSpec postSpec = mock(WebClient.RequestBodyUriSpec.class);
    WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);

    when(webClient.post()).thenReturn(postSpec);
    when(postSpec.uri("https://google/revoke")).thenReturn(bodySpec);
    when(bodySpec.contentType(MediaType.APPLICATION_FORM_URLENCODED)).thenReturn(bodySpec);
    when(bodySpec.bodyValue(any())).thenThrow(new RuntimeException("boom"));

    assertThatThrownBy(() -> adapter.revokeAccessToken("access-token"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Google revoke request failed");
  }

  @Test
  void revokeAccessToken_rethrowsBusinessException_withoutWrapping() {
    WebClient.RequestBodyUriSpec postSpec = mock(WebClient.RequestBodyUriSpec.class);
    WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
    BusinessException expected =
        new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "already business exception");

    when(webClient.post()).thenReturn(postSpec);
    when(postSpec.uri("https://google/revoke")).thenReturn(bodySpec);
    when(bodySpec.contentType(MediaType.APPLICATION_FORM_URLENCODED)).thenReturn(bodySpec);
    when(bodySpec.bodyValue(any())).thenThrow(expected);

    assertThatThrownBy(() -> adapter.revokeAccessToken("access-token")).isSameAs(expected);
  }

  @Test
  void getAccessToken_wrapsUnexpectedExceptionFromClient() {
    when(webClient.post()).thenThrow(new RuntimeException("network"));

    assertThatThrownBy(() -> adapter.getAccessToken("auth-code"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Google token request failed");
  }

  @Test
  void getAccessToken_rethrowsBusinessException_withoutWrapping() {
    BusinessException expected =
        new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "already business exception");
    when(webClient.post()).thenThrow(expected);

    assertThatThrownBy(() -> adapter.getAccessToken("auth-code")).isSameAs(expected);
  }
}
