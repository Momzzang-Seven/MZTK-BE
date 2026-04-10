package momzzangseven.mztkbe.modules.admin.infrastructure.external.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.user.application.port.out.SaveUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateAdminUserAdapter 단위 테스트")
class CreateAdminUserAdapterTest {

  @Mock private SaveUserPort saveUserPort;
  @Captor private ArgumentCaptor<User> userCaptor;

  private CreateAdminUserAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new CreateAdminUserAdapter(saveUserPort);
  }

  @Test
  @DisplayName("[M-94] createAdmin delegates to SaveUserPort and returns userId")
  void createAdmin_delegatesToSaveUserPortAndReturnsUserId() {
    // given
    User savedUser =
        User.builder()
            .id(42L)
            .email("admin@test.com")
            .nickname("TestAdmin")
            .role(UserRole.ADMIN_SEED)
            .createdAt(java.time.Instant.now())
            .updatedAt(java.time.Instant.now())
            .build();
    given(saveUserPort.saveUser(any(User.class))).willReturn(savedUser);

    // when
    Long userId = adapter.createAdmin("admin@test.com", "TestAdmin", UserRole.ADMIN_SEED);

    // then
    assertThat(userId).isEqualTo(42L);
    verify(saveUserPort, times(1)).saveUser(userCaptor.capture());

    User capturedUser = userCaptor.getValue();
    assertThat(capturedUser.getEmail()).isEqualTo("admin@test.com");
    assertThat(capturedUser.getNickname()).isEqualTo("TestAdmin");
    assertThat(capturedUser.getRole()).isEqualTo(UserRole.ADMIN_SEED);
  }

  @Test
  @DisplayName("[M-95] createAdmin with ADMIN_GENERATED role delegates correctly")
  void createAdmin_adminGeneratedRole_delegatesCorrectly() {
    // given
    User savedUser =
        User.builder()
            .id(100L)
            .email("gen@admin.local")
            .nickname("GenAdmin")
            .role(UserRole.ADMIN_GENERATED)
            .createdAt(java.time.Instant.now())
            .updatedAt(java.time.Instant.now())
            .build();
    given(saveUserPort.saveUser(any(User.class))).willReturn(savedUser);

    // when
    Long userId = adapter.createAdmin("gen@admin.local", "GenAdmin", UserRole.ADMIN_GENERATED);

    // then
    assertThat(userId).isEqualTo(100L);
    verify(saveUserPort).saveUser(userCaptor.capture());
    assertThat(userCaptor.getValue().getRole()).isEqualTo(UserRole.ADMIN_GENERATED);
  }

  @Test
  @DisplayName("[M-96] createAdmin passes User.createAdmin() factory output to SaveUserPort")
  void createAdmin_passesFactoryOutputToSaveUserPort() {
    // given
    User savedUser =
        User.builder()
            .id(1L)
            .email("admin@test.com")
            .nickname("Admin")
            .role(UserRole.ADMIN_SEED)
            .createdAt(java.time.Instant.now())
            .updatedAt(java.time.Instant.now())
            .build();
    given(saveUserPort.saveUser(any(User.class))).willReturn(savedUser);

    // when
    adapter.createAdmin("admin@test.com", "Admin", UserRole.ADMIN_SEED);

    // then
    verify(saveUserPort).saveUser(userCaptor.capture());
    User capturedUser = userCaptor.getValue();
    assertThat(capturedUser.getProfileImageUrl()).isNull();
    assertThat(capturedUser.getCreatedAt()).isNotNull();
    assertThat(capturedUser.getUpdatedAt()).isNotNull();
  }
}
