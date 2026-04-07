package momzzangseven.mztkbe.modules.user.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UserInfo DTO unit test")
class UserInfoTest {

  @Test
  @DisplayName("[M-37] UserInfo.from: User 도메인 모델의 프로필 필드만 매핑")
  void from_mapsOnlyProfileFields() {
    // given — User with profile fields
    Instant now = Instant.now();
    User user =
        User.builder()
            .id(1L)
            .email("test@example.com")
            .nickname("tester")
            .profileImageUrl("https://img.com/1.png")
            .role(UserRole.TRAINER)
            .createdAt(now.minus(10, ChronoUnit.DAYS))
            .updatedAt(now.minus(1, ChronoUnit.DAYS))
            .build();

    // when
    UserInfo info = UserInfo.from(user);

    // then — profile fields are mapped correctly
    assertThat(info.id()).isEqualTo(1L);
    assertThat(info.email()).isEqualTo("test@example.com");
    assertThat(info.nickname()).isEqualTo("tester");
    assertThat(info.profileImageUrl()).isEqualTo("https://img.com/1.png");
    assertThat(info.role()).isEqualTo(UserRole.TRAINER);
    assertThat(info.createdAt()).isEqualTo(now.minus(10, ChronoUnit.DAYS));
    assertThat(info.updatedAt()).isEqualTo(now.minus(1, ChronoUnit.DAYS));
  }
}
