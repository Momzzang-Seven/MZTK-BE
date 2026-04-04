package momzzangseven.mztkbe.modules.account.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.account.application.dto.AccountUserSnapshot;
import momzzangseven.mztkbe.modules.user.application.dto.CreateUserCommand;
import momzzangseven.mztkbe.modules.user.application.dto.UserInfo;
import momzzangseven.mztkbe.modules.user.application.port.in.CreateUserUseCase;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("UserLifecycleAdapter")
@ExtendWith(MockitoExtension.class)
class UserLifecycleAdapterTest {

  @Mock private CreateUserUseCase createUserUseCase;

  @InjectMocks private UserLifecycleAdapter userLifecycleAdapter;

  @Test
  @DisplayName("should create user and convert to AccountUserSnapshot")
  void shouldCreateAndConvert() {
    UserInfo createdUserInfo =
        new UserInfo(
            10L,
            "new@example.com",
            "newuser",
            null,
            UserRole.USER,
            LocalDateTime.now(),
            LocalDateTime.now());
    when(createUserUseCase.createUser(any(CreateUserCommand.class))).thenReturn(createdUserInfo);

    AccountUserSnapshot result =
        userLifecycleAdapter.createUser("new@example.com", "newuser", null, "USER");

    assertThat(result.userId()).isEqualTo(10L);
    assertThat(result.email()).isEqualTo("new@example.com");
    assertThat(result.nickname()).isEqualTo("newuser");
    assertThat(result.profileImageUrl()).isNull();
    assertThat(result.role()).isEqualTo("USER");
  }
}
