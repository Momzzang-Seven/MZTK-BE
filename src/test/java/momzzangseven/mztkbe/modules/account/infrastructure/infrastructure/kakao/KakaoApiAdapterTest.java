package momzzangseven.mztkbe.modules.account.infrastructure.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.modules.account.application.dto.KakaoUserInfo;
import momzzangseven.mztkbe.modules.account.infrastructure.config.AuthWebClientProperties;
import momzzangseven.mztkbe.modules.account.infrastructure.kakao.dto.KakaoTokenResponse;
import momzzangseven.mztkbe.modules.account.infrastructure.kakao.dto.KakaoUserResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@SuppressWarnings("unchecked")
class KakaoApiAdapterTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private WebClient webClient;
  private KakaoApiAdapter adapter;

  private KakaoAuthProperties properties;
  private AuthWebClientProperties webClientProperties;

  @BeforeEach
  void setUp() {
    webClient = mock(WebClient.class);

    properties = new KakaoAuthProperties();
    properties.getAuth().setClient("kakao-client");
    properties.getAuth().setRedirect("http://localhost/callback");
    properties.getApi().setTokenUri("https://kakao/token");
    properties.getApi().setUserinfoUri("https://kakao/userinfo");
    properties.getApi().setUnlinkUri("https://kakao/unlink");
    properties.getApi().setAdminKey("admin-key");

    webClientProperties = new AuthWebClientProperties();
    webClientProperties.setBlockTimeoutSeconds(1);

    adapter = new KakaoApiAdapter(properties, webClient, webClientProperties);
  }

  @Test
  void getAccessToken_returnsAccessToken_whenResponseValid() throws Exception {
    WebClient.RequestBodyUriSpec postSpec = mock(WebClient.RequestBodyUriSpec.class);
    WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
    WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
    WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

    KakaoTokenResponse tokenResponse =
        objectMapper.readValue(
            """
            {"access_token":"access-token","refresh_token":"refresh-token"}
            """,
            KakaoTokenResponse.class);

    when(webClient.post()).thenReturn(postSpec);
    when(postSpec.uri("https://kakao/token")).thenReturn(bodySpec);
    when(bodySpec.contentType(MediaType.APPLICATION_FORM_URLENCODED)).thenReturn(bodySpec);
    when(bodySpec.bodyValue(any())).thenReturn(headersSpec);
    when(headersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    when(responseSpec.bodyToMono(KakaoTokenResponse.class)).thenReturn(Mono.just(tokenResponse));

    String accessToken = adapter.getAccessToken("auth-code");

    assertThat(accessToken).isEqualTo("access-token");
    verify(bodySpec).contentType(MediaType.APPLICATION_FORM_URLENCODED);
  }

  @Test
  void getAccessToken_throws_whenResponseTokenMissing() {
    WebClient.RequestBodyUriSpec postSpec = mock(WebClient.RequestBodyUriSpec.class);
    WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
    WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
    WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

    when(webClient.post()).thenReturn(postSpec);
    when(postSpec.uri("https://kakao/token")).thenReturn(bodySpec);
    when(bodySpec.contentType(MediaType.APPLICATION_FORM_URLENCODED)).thenReturn(bodySpec);
    when(bodySpec.bodyValue(any())).thenReturn(headersSpec);
    when(headersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    when(responseSpec.bodyToMono(KakaoTokenResponse.class)).thenReturn(Mono.empty());

    assertThatThrownBy(() -> adapter.getAccessToken("auth-code"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Failed to get Kakao access token");
  }

  @Test
  void getAccessToken_throws_whenResponseAccessTokenIsNull() {
    WebClient.RequestBodyUriSpec postSpec = mock(WebClient.RequestBodyUriSpec.class);
    WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
    WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
    WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
    KakaoTokenResponse tokenResponse = mock(KakaoTokenResponse.class);

    when(tokenResponse.getAccessToken()).thenReturn(null);
    when(webClient.post()).thenReturn(postSpec);
    when(postSpec.uri("https://kakao/token")).thenReturn(bodySpec);
    when(bodySpec.contentType(MediaType.APPLICATION_FORM_URLENCODED)).thenReturn(bodySpec);
    when(bodySpec.bodyValue(any())).thenReturn(headersSpec);
    when(headersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    when(responseSpec.bodyToMono(KakaoTokenResponse.class)).thenReturn(Mono.just(tokenResponse));

    assertThatThrownBy(() -> adapter.getAccessToken("auth-code"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Failed to get Kakao access token");
  }

  @Test
  void getAccessToken_rethrowsBusinessException_withoutWrapping() {
    BusinessException expected =
        new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "already business exception");
    when(webClient.post()).thenThrow(expected);

    assertThatThrownBy(() -> adapter.getAccessToken("auth-code")).isSameAs(expected);
  }

  @Test
  void getUserInfo_mapsNullableNestedFieldsSafely() throws Exception {
    WebClient.RequestHeadersUriSpec getSpec = mock(WebClient.RequestHeadersUriSpec.class);
    WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
    WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

    KakaoUserResponse userResponse =
        objectMapper.readValue(
            """
            {"id":12345}
            """, KakaoUserResponse.class);

    when(webClient.get()).thenReturn(getSpec);
    when(getSpec.uri("https://kakao/userinfo")).thenReturn(headersSpec);
    when(headersSpec.headers(any())).thenReturn(headersSpec);
    when(headersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    when(responseSpec.bodyToMono(KakaoUserResponse.class)).thenReturn(Mono.just(userResponse));

    KakaoUserInfo userInfo = adapter.getUserInfo("access-token");

    assertThat(userInfo.getProviderUserId()).isEqualTo("12345");
    assertThat(userInfo.getEmail()).isNull();
    assertThat(userInfo.getNickname()).isNull();
    assertThat(userInfo.getProfileImageUrl()).isNull();
  }

  @Test
  void getUserInfo_mapsNestedFields_whenNestedObjectsPresent() throws Exception {
    WebClient.RequestHeadersUriSpec getSpec = mock(WebClient.RequestHeadersUriSpec.class);
    WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
    WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

    KakaoUserResponse userResponse =
        objectMapper.readValue(
            """
            {
              "id":12345,
              "kakao_account":{"email":"user@example.com"},
              "properties":{"nickname":"User","profile_image":"http://img"}
            }
            """,
            KakaoUserResponse.class);

    when(webClient.get()).thenReturn(getSpec);
    when(getSpec.uri("https://kakao/userinfo")).thenReturn(headersSpec);
    when(headersSpec.headers(any())).thenReturn(headersSpec);
    when(headersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    when(responseSpec.bodyToMono(KakaoUserResponse.class)).thenReturn(Mono.just(userResponse));

    KakaoUserInfo userInfo = adapter.getUserInfo("access-token");

    assertThat(userInfo.getProviderUserId()).isEqualTo("12345");
    assertThat(userInfo.getEmail()).isEqualTo("user@example.com");
    assertThat(userInfo.getNickname()).isEqualTo("User");
    assertThat(userInfo.getProfileImageUrl()).isEqualTo("http://img");
  }

  @Test
  void getUserInfo_throws_whenResponseBodyIsNull() {
    WebClient.RequestHeadersUriSpec getSpec = mock(WebClient.RequestHeadersUriSpec.class);
    WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
    WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

    when(webClient.get()).thenReturn(getSpec);
    when(getSpec.uri("https://kakao/userinfo")).thenReturn(headersSpec);
    when(headersSpec.headers(any())).thenReturn(headersSpec);
    when(headersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    when(responseSpec.bodyToMono(KakaoUserResponse.class)).thenReturn(Mono.empty());

    assertThatThrownBy(() -> adapter.getUserInfo("access-token"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Failed to get Kakao user info");
  }

  @Test
  void getUserInfo_rethrowsBusinessException_withoutWrapping() {
    BusinessException expected =
        new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "already business exception");
    when(webClient.get()).thenThrow(expected);

    assertThatThrownBy(() -> adapter.getUserInfo("access-token")).isSameAs(expected);
  }

  @Test
  void unlinkUser_throws_whenProviderUserIdBlank() {
    assertThatThrownBy(() -> adapter.unlinkUser(" "))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("providerUserId is required");
  }

  @Test
  void unlinkUser_throws_whenProviderUserIdNull() {
    assertThatThrownBy(() -> adapter.unlinkUser(null))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("providerUserId is required");
  }

  @Test
  void unlinkUser_throws_whenUnlinkUriMissing() {
    properties.getApi().setUnlinkUri(" ");

    assertThatThrownBy(() -> adapter.unlinkUser("123"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("kakao.api.unlink-uri is required");
  }

  @Test
  void unlinkUser_throws_whenAdminKeyMissing() {
    properties.getApi().setAdminKey(" ");

    assertThatThrownBy(() -> adapter.unlinkUser("123"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("kakao.api.admin-key is required");
  }

  @Test
  void unlinkUser_callsExternalApi_whenInputValid() {
    WebClient.RequestBodyUriSpec postSpec = mock(WebClient.RequestBodyUriSpec.class);
    WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
    WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
    WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

    when(webClient.post()).thenReturn(postSpec);
    when(postSpec.uri("https://kakao/unlink")).thenReturn(bodySpec);
    when(bodySpec.header(eq("Authorization"), eq("KakaoAK admin-key"))).thenReturn(bodySpec);
    when(bodySpec.contentType(MediaType.APPLICATION_FORM_URLENCODED)).thenReturn(bodySpec);
    when(bodySpec.bodyValue(any())).thenReturn(headersSpec);
    when(headersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    when(responseSpec.toBodilessEntity()).thenReturn(Mono.just(ResponseEntity.ok().build()));

    adapter.unlinkUser("123");

    verify(bodySpec).header("Authorization", "KakaoAK admin-key");
  }

  @Test
  void unlinkUser_wrapsUnexpectedException() {
    WebClient.RequestBodyUriSpec postSpec = mock(WebClient.RequestBodyUriSpec.class);
    WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);

    when(webClient.post()).thenReturn(postSpec);
    when(postSpec.uri("https://kakao/unlink")).thenReturn(bodySpec);
    when(bodySpec.header(eq("Authorization"), eq("KakaoAK admin-key"))).thenReturn(bodySpec);
    when(bodySpec.contentType(MediaType.APPLICATION_FORM_URLENCODED)).thenReturn(bodySpec);
    when(bodySpec.bodyValue(any())).thenThrow(new RuntimeException("boom"));

    assertThatThrownBy(() -> adapter.unlinkUser("123"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Kakao unlink request failed");
  }

  @Test
  void unlinkUser_rethrowsBusinessException_withoutWrapping() {
    WebClient.RequestBodyUriSpec postSpec = mock(WebClient.RequestBodyUriSpec.class);
    WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
    BusinessException expected =
        new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "already business exception");

    when(webClient.post()).thenReturn(postSpec);
    when(postSpec.uri("https://kakao/unlink")).thenReturn(bodySpec);
    when(bodySpec.header(eq("Authorization"), eq("KakaoAK admin-key"))).thenReturn(bodySpec);
    when(bodySpec.contentType(MediaType.APPLICATION_FORM_URLENCODED)).thenReturn(bodySpec);
    when(bodySpec.bodyValue(any())).thenThrow(expected);

    assertThatThrownBy(() -> adapter.unlinkUser("123")).isSameAs(expected);
  }

  @Test
  void getAccessToken_sendsExpectedFormFields() {
    WebClient.RequestBodyUriSpec postSpec = mock(WebClient.RequestBodyUriSpec.class);
    WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
    WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
    WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

    KakaoTokenResponse tokenResponse = mock(KakaoTokenResponse.class);
    when(tokenResponse.getAccessToken()).thenReturn("access-token");

    when(webClient.post()).thenReturn(postSpec);
    when(postSpec.uri("https://kakao/token")).thenReturn(bodySpec);
    when(bodySpec.contentType(MediaType.APPLICATION_FORM_URLENCODED)).thenReturn(bodySpec);
    when(bodySpec.bodyValue(any())).thenReturn(headersSpec);
    when(headersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    when(responseSpec.bodyToMono(KakaoTokenResponse.class)).thenReturn(Mono.just(tokenResponse));

    adapter.getAccessToken("code-123");

    org.mockito.ArgumentCaptor<Object> captor = org.mockito.ArgumentCaptor.forClass(Object.class);
    verify(bodySpec).bodyValue(captor.capture());

    Object formObj = captor.getValue();
    Assertions.assertInstanceOf(MultiValueMap.class, formObj);
    MultiValueMap<String, String> form = (MultiValueMap<String, String>) formObj;
    assertThat(form.get("grant_type")).containsExactly("authorization_code");
    assertThat(form.get("client_id")).containsExactly("kakao-client");
    assertThat(form.get("redirect_uri")).containsExactly("http://localhost/callback");
    assertThat(form.get("code")).containsExactly("code-123");
  }
}
