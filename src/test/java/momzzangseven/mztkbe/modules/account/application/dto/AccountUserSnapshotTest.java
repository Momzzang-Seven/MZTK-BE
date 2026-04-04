package momzzangseven.mztkbe.modules.account.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AccountUserSnapshot")
class AccountUserSnapshotTest {

  @Test
  @DisplayName("should hold all fields correctly")
  void shouldHoldAllFields() {
    AccountUserSnapshot snapshot =
        new AccountUserSnapshot(
            1L, "test@example.com", "tester", "https://img.url/pic.jpg", "USER");

    assertThat(snapshot.userId()).isEqualTo(1L);
    assertThat(snapshot.email()).isEqualTo("test@example.com");
    assertThat(snapshot.nickname()).isEqualTo("tester");
    assertThat(snapshot.profileImageUrl()).isEqualTo("https://img.url/pic.jpg");
    assertThat(snapshot.role()).isEqualTo("USER");
  }

  @Test
  @DisplayName("should support null profileImageUrl")
  void shouldSupportNullProfileImageUrl() {
    AccountUserSnapshot snapshot =
        new AccountUserSnapshot(2L, "user@example.com", "nick", null, "ADMIN");

    assertThat(snapshot.profileImageUrl()).isNull();
  }
}
