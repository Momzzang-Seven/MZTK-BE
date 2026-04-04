package momzzangseven.mztkbe.modules.user.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import momzzangseven.mztkbe.modules.user.domain.model.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UserInfo DTO unit test")
class UserInfoTest {

  @Test
  @DisplayName("[M-37] UserInfo.from: User 도메인 모델의 프로필 필드만 매핑")
  void from_mapsOnlyProfileFields() {
    // given — User with all fields including auth-related ones
    LocalDateTime now = LocalDateTime.now();
    User user =
        User.builder()
            .id(1L)
            .email("test@example.com")
            .password("$2a$" + "a".repeat(56))
            .nickname("tester")
            .profileImageUrl("https://img.com/1.png")
            .providerUserId("provider-123")
            .googleRefreshToken("encrypted-token")
            .authProvider(AuthProvider.GOOGLE)
            .role(UserRole.TRAINER)
            .status(UserStatus.DELETED)
            .lastLoginAt(now.minusHours(1))
            .deletedAt(now.minusMinutes(30))
            .createdAt(now.minusDays(10))
            .updatedAt(now.minusDays(1))
            .build();

    // when
    UserInfo info = UserInfo.from(user);

    // then — profile fields are mapped correctly
    assertThat(info.id()).isEqualTo(1L);
    assertThat(info.email()).isEqualTo("test@example.com");
    assertThat(info.nickname()).isEqualTo("tester");
    assertThat(info.profileImageUrl()).isEqualTo("https://img.com/1.png");
    assertThat(info.role()).isEqualTo(UserRole.TRAINER);
    assertThat(info.createdAt()).isEqualTo(now.minusDays(10));
    assertThat(info.updatedAt()).isEqualTo(now.minusDays(1));

    // auth-related fields (password, authProvider, status, deletedAt) are NOT part of UserInfo
    // This is enforced at compile time — UserInfo record has no such fields
  }
}
