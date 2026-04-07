package momzzangseven.mztkbe.modules.account.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GoogleUserInfo unit test")
class GoogleUserInfoTest {

  @Test
  @DisplayName("builder sets all fields")
  void builder_setsAllFields() {
    GoogleUserInfo info =
        GoogleUserInfo.builder()
            .providerUserId("google-123")
            .email("user@example.com")
            .nickname("tester")
            .profileImageUrl("https://image")
            .build();

    assertThat(info.getProviderUserId()).isEqualTo("google-123");
    assertThat(info.getEmail()).isEqualTo("user@example.com");
    assertThat(info.getNickname()).isEqualTo("tester");
    assertThat(info.getProfileImageUrl()).isEqualTo("https://image");
  }

  @Test
  @DisplayName("builder keeps null optional fields")
  void builder_allowsNullOptionalFields() {
    GoogleUserInfo info = GoogleUserInfo.builder().providerUserId("google-123").build();

    assertThat(info.getProviderUserId()).isEqualTo("google-123");
    assertThat(info.getEmail()).isNull();
    assertThat(info.getNickname()).isNull();
    assertThat(info.getProfileImageUrl()).isNull();
  }
}
