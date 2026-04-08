package momzzangseven.mztkbe.modules.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.UserNotFoundException;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetUserRoleService 단위 테스트")
class GetUserRoleServiceTest {

  @Mock private LoadUserPort loadUserPort;

  private GetUserRoleService service;

  @BeforeEach
  void setUp() {
    service = new GetUserRoleService(loadUserPort);
  }

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("[M-44] 존재하는 사용자 → role 반환 (TRAINER)")
    void getUserRole_existingUser_returnsRole() {
      // given
      User user = buildUser(1L, UserRole.TRAINER);
      given(loadUserPort.loadUserById(1L)).willReturn(Optional.of(user));

      // when
      UserRole role = service.getUserRole(1L);

      // then
      assertThat(role).isEqualTo(UserRole.TRAINER);
    }

    @Test
    @DisplayName("[M-46] ADMIN 역할 정확 반환")
    void getUserRole_adminUser_returnsAdmin() {
      // given
      User user = buildUser(2L, UserRole.ADMIN);
      given(loadUserPort.loadUserById(2L)).willReturn(Optional.of(user));

      // when
      UserRole role = service.getUserRole(2L);

      // then
      assertThat(role).isEqualTo(UserRole.ADMIN);
    }
  }

  @Nested
  @DisplayName("실패 케이스")
  class FailureCases {

    @Test
    @DisplayName("[M-45] 미존재 사용자 → UserNotFoundException")
    void getUserRole_nonExistingUser_throwsException() {
      // given
      given(loadUserPort.loadUserById(999L)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> service.getUserRole(999L)).isInstanceOf(UserNotFoundException.class);
    }
  }

  private User buildUser(Long id, UserRole role) {
    Instant now = Instant.now();
    return User.builder()
        .id(id)
        .email("test@example.com")
        .nickname("tester")
        .role(role)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }
}
