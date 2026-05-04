package momzzangseven.mztkbe.modules.admin.user.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.modules.admin.user.api.dto.GetAdminUsersRequestDTO;
import momzzangseven.mztkbe.modules.admin.user.domain.vo.AdminUserAccountStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetAdminUsersRequestDTO 단위 테스트")
class GetAdminUsersRequestDTOTest {

  @Test
  @DisplayName("blank search 와 null page/size/sort 를 기본 정책으로 정규화한다")
  void toCommand_normalizesDefaults() {
    GetAdminUsersCommand command =
        new GetAdminUsersRequestDTO(
                "   ", AdminUserAccountStatus.ACTIVE, AdminUserRoleFilter.USER, null, null, null)
            .toCommand(9L);

    assertThat(command.search()).isNull();
    assertThat(command.page()).isZero();
    assertThat(command.size()).isEqualTo(20);
    assertThat(command.sortKey()).isEqualTo(AdminUserSortKey.JOINED_AT);
  }

  @Test
  @DisplayName("whitelist 밖 sort 값이면 IllegalArgumentException")
  void toCommand_unsupportedSort_throws() {
    GetAdminUsersRequestDTO request =
        new GetAdminUsersRequestDTO(null, null, null, 0, 20, "createdAt");

    assertThatThrownBy(() -> request.toCommand(9L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported sort value");
  }

  @Test
  @DisplayName("search 길이가 100자를 초과하면 IllegalArgumentException")
  void toCommand_tooLongSearch_throws() {
    String search = "a".repeat(101);

    assertThatThrownBy(
            () -> new GetAdminUsersRequestDTO(search, null, null, 0, 20, null).toCommand(9L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("100 characters or fewer");
  }
}
