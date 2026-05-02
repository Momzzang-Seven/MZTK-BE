package momzzangseven.mztkbe.modules.admin.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.admin.user.application.dto.AdminUserRoleFilter;
import momzzangseven.mztkbe.modules.admin.user.application.dto.AdminUserSortKey;
import momzzangseven.mztkbe.modules.admin.user.application.dto.GetAdminUsersCommand;
import momzzangseven.mztkbe.modules.admin.user.application.port.out.LoadAdminUserCommentCountsPort;
import momzzangseven.mztkbe.modules.admin.user.application.port.out.LoadAdminUserPostCountsPort;
import momzzangseven.mztkbe.modules.admin.user.application.port.out.LoadAdminUserStatusesPort;
import momzzangseven.mztkbe.modules.admin.user.application.port.out.LoadAdminUsersPort;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetAdminUsersService 단위 테스트")
class GetAdminUsersServiceTest {

  @Mock private LoadAdminUsersPort loadAdminUsersPort;
  @Mock private LoadAdminUserStatusesPort loadAdminUserStatusesPort;
  @Mock private LoadAdminUserPostCountsPort loadAdminUserPostCountsPort;
  @Mock private LoadAdminUserCommentCountsPort loadAdminUserCommentCountsPort;

  @InjectMocks private GetAdminUsersService service;

  @Test
  @DisplayName("status filter, count 조합, postCount 정렬을 service 에서 수행한다")
  void execute_combinesStatusAndCounts() {
    GetAdminUsersCommand command =
        new GetAdminUsersCommand(
            9L,
            "alpha",
            AccountStatus.ACTIVE,
            AdminUserRoleFilter.USER,
            0,
            20,
            AdminUserSortKey.POST_COUNT);

    given(loadAdminUserStatusesPort.load(null, AccountStatus.ACTIVE))
        .willReturn(Map.of(10L, AccountStatus.ACTIVE, 11L, AccountStatus.ACTIVE));
    given(
            loadAdminUsersPort.load(
                new LoadAdminUsersPort.AdminUserProfileQuery(
                    "alpha", AdminUserRoleFilter.USER, Set.of(10L, 11L))))
        .willReturn(
            List.of(
                new LoadAdminUsersPort.AdminUserProfileView(
                    10L,
                    "alpha-1",
                    UserRole.USER,
                    "alpha1@test.com",
                    Instant.parse("2025-01-10T00:00:00Z")),
                new LoadAdminUsersPort.AdminUserProfileView(
                    11L,
                    "alpha-2",
                    UserRole.USER,
                    "alpha2@test.com",
                    Instant.parse("2025-01-11T00:00:00Z"))));
    given(loadAdminUserStatusesPort.load(List.of(10L, 11L), AccountStatus.ACTIVE))
        .willReturn(Map.of(10L, AccountStatus.ACTIVE, 11L, AccountStatus.ACTIVE));
    given(loadAdminUserPostCountsPort.load(List.of(10L, 11L))).willReturn(Map.of(10L, 3L, 11L, 7L));
    given(loadAdminUserCommentCountsPort.load(List.of(10L, 11L)))
        .willReturn(Map.of(10L, 1L, 11L, 2L));

    var result = service.execute(command);

    assertThat(result.getTotalElements()).isEqualTo(2L);
    assertThat(result.getContent()).extracting(item -> item.userId()).containsExactly(11L, 10L);
    assertThat(result.getContent().get(0).postCount()).isEqualTo(7L);
    assertThat(result.getContent().get(0).commentCount()).isEqualTo(2L);
  }
}
