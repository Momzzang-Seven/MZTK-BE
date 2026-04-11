package momzzangseven.mztkbe.modules.admin.infrastructure.external.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import momzzangseven.mztkbe.modules.admin.domain.vo.AdminRole;
import momzzangseven.mztkbe.modules.user.application.dto.CreateUserCommand;
import momzzangseven.mztkbe.modules.user.application.dto.UserInfo;
import momzzangseven.mztkbe.modules.user.application.port.in.CreateUserUseCase;
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

  @Mock private CreateUserUseCase createUserUseCase;
  @Captor private ArgumentCaptor<CreateUserCommand> commandCaptor;

  private CreateAdminUserAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new CreateAdminUserAdapter(createUserUseCase);
  }

  @Test
  @DisplayName("[M-94] createAdmin delegates to CreateUserUseCase and returns userId")
  void createAdmin_delegatesToSaveUserPortAndReturnsUserId() {
    // given
    UserInfo userInfo =
        new UserInfo(
            42L,
            "admin@test.com",
            "TestAdmin",
            null,
            UserRole.ADMIN_SEED,
            Instant.now(),
            Instant.now());
    given(createUserUseCase.createAdminUser(any(CreateUserCommand.class))).willReturn(userInfo);

    // when
    Long userId = adapter.createAdmin("admin@test.com", "TestAdmin", AdminRole.ADMIN_SEED);

    // then
    assertThat(userId).isEqualTo(42L);
    verify(createUserUseCase, times(1)).createAdminUser(commandCaptor.capture());

    CreateUserCommand capturedCommand = commandCaptor.getValue();
    assertThat(capturedCommand.email()).isEqualTo("admin@test.com");
    assertThat(capturedCommand.nickname()).isEqualTo("TestAdmin");
    assertThat(capturedCommand.role()).isEqualTo(AdminRole.ADMIN_SEED.toString());
  }

  @Test
  @DisplayName("[M-95] createAdmin with ADMIN_GENERATED role delegates correctly")
  void createAdmin_adminGeneratedRole_delegatesCorrectly() {
    // given
    UserInfo userInfo =
        new UserInfo(
            100L,
            "gen@admin.local",
            "GenAdmin",
            null,
            UserRole.ADMIN_GENERATED,
            Instant.now(),
            Instant.now());
    given(createUserUseCase.createAdminUser(any(CreateUserCommand.class))).willReturn(userInfo);

    // when
    Long userId = adapter.createAdmin("gen@admin.local", "GenAdmin", AdminRole.ADMIN_GENERATED);

    // then
    assertThat(userId).isEqualTo(100L);
    verify(createUserUseCase).createAdminUser(commandCaptor.capture());
    assertThat(commandCaptor.getValue().role()).isEqualTo(AdminRole.ADMIN_GENERATED.toString());
  }

  @Test
  @DisplayName("[M-96] createAdmin passes CreateUserCommand to CreateUserUseCase")
  void createAdmin_passesFactoryOutputToSaveUserPort() {
    // given
    UserInfo userInfo =
        new UserInfo(
            1L, "admin@test.com", "Admin", null, UserRole.ADMIN_SEED, Instant.now(), Instant.now());
    given(createUserUseCase.createAdminUser(any(CreateUserCommand.class))).willReturn(userInfo);

    // when
    adapter.createAdmin("admin@test.com", "Admin", AdminRole.ADMIN_SEED);

    // then
    verify(createUserUseCase).createAdminUser(commandCaptor.capture());
    CreateUserCommand capturedCommand = commandCaptor.getValue();
    assertThat(capturedCommand.profileImageUrl()).isNull();
    assertThat(capturedCommand.email()).isEqualTo("admin@test.com");
    assertThat(capturedCommand.nickname()).isEqualTo("Admin");
  }
}
