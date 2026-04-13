package momzzangseven.mztkbe.modules.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import momzzangseven.mztkbe.modules.admin.application.dto.AdminAccountSummary;
import momzzangseven.mztkbe.modules.admin.application.port.out.LoadAdminAccountPort;
import momzzangseven.mztkbe.modules.admin.domain.model.AdminAccount;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ListAdminAccountsService 단위 테스트")
class ListAdminAccountsServiceTest {

  @Mock private LoadAdminAccountPort loadAdminAccountPort;
  @Mock private LoadUserPort loadUserPort;

  @InjectMocks private ListAdminAccountsService service;

  private AdminAccount buildAccount(
      Long userId, String loginId, Long createdBy, Instant lastLogin, Instant passwordRotated) {
    return AdminAccount.builder()
        .id(userId)
        .userId(userId)
        .loginId(loginId)
        .passwordHash("$2a$10$hash")
        .createdBy(createdBy)
        .lastLoginAt(lastLogin)
        .passwordLastRotatedAt(passwordRotated)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
  }

  private User buildUser(Long id, UserRole role) {
    return User.builder()
        .id(id)
        .email("admin-" + id + "@test.local")
        .nickname("Admin-" + id)
        .role(role)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
  }

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("[M-130] execute returns all active accounts with correct isSeed flag")
    void execute_multipleAccounts_returnsCorrectIsSeedFlag() {
      // given
      AdminAccount seedAccount = buildAccount(1L, "seed001", null, null, Instant.now());
      AdminAccount genAccount = buildAccount(2L, "gen001", 1L, null, Instant.now());
      given(loadAdminAccountPort.findAllActive()).willReturn(List.of(seedAccount, genAccount));
      given(loadUserPort.loadUsersByIds(List.of(1L, 2L)))
          .willReturn(
              List.of(buildUser(1L, UserRole.ADMIN_SEED), buildUser(2L, UserRole.ADMIN_GENERATED)));

      // when
      List<AdminAccountSummary> result = service.execute(1L);

      // then
      assertThat(result).hasSize(2);
      assertThat(result.get(0).userId()).isEqualTo(1L);
      assertThat(result.get(0).loginId()).isEqualTo("seed001");
      assertThat(result.get(0).isSeed()).isTrue();
      assertThat(result.get(1).userId()).isEqualTo(2L);
      assertThat(result.get(1).loginId()).isEqualTo("gen001");
      assertThat(result.get(1).isSeed()).isFalse();
    }

    @Test
    @DisplayName("[M-133] execute maps createdBy, lastLoginAt, and passwordLastRotatedAt correctly")
    void execute_mapsAllFieldsCorrectly() {
      // given
      Instant lastLogin = Instant.parse("2026-01-15T10:30:00Z");
      Instant passwordRotated = Instant.parse("2026-02-20T14:00:00Z");
      AdminAccount account = buildAccount(10L, "admin010", 5L, lastLogin, passwordRotated);
      given(loadAdminAccountPort.findAllActive()).willReturn(List.of(account));
      given(loadUserPort.loadUsersByIds(List.of(10L)))
          .willReturn(List.of(buildUser(10L, UserRole.ADMIN_GENERATED)));

      // when
      List<AdminAccountSummary> result = service.execute(1L);

      // then
      assertThat(result).hasSize(1);
      AdminAccountSummary summary = result.get(0);
      assertThat(summary.createdBy()).isEqualTo(5L);
      assertThat(summary.lastLoginAt()).isEqualTo(lastLogin);
      assertThat(summary.passwordLastRotatedAt()).isEqualTo(passwordRotated);
    }
  }

  @Nested
  @DisplayName("엣지 케이스")
  class EdgeCases {

    @Test
    @DisplayName("[M-131] execute returns empty list when no active accounts exist")
    void execute_noActiveAccounts_returnsEmptyList() {
      // given
      given(loadAdminAccountPort.findAllActive()).willReturn(Collections.emptyList());
      given(loadUserPort.loadUsersByIds(Collections.emptyList()))
          .willReturn(Collections.emptyList());

      // when
      List<AdminAccountSummary> result = service.execute(1L);

      // then
      assertThat(result).isEmpty();
      verify(loadUserPort).loadUsersByIds(Collections.emptyList());
    }

    @Test
    @DisplayName("[M-132] execute sets isSeed to false when user not found in loaded users")
    void execute_userNotFound_isSeedFalse() {
      // given
      AdminAccount account = buildAccount(999L, "orphan01", null, null, Instant.now());
      given(loadAdminAccountPort.findAllActive()).willReturn(List.of(account));
      given(loadUserPort.loadUsersByIds(List.of(999L))).willReturn(Collections.emptyList());

      // when
      List<AdminAccountSummary> result = service.execute(1L);

      // then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).isSeed()).isFalse();
    }
  }
}
