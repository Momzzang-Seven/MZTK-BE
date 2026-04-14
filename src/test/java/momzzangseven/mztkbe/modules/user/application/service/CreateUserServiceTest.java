package momzzangseven.mztkbe.modules.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.user.application.dto.CreateUserCommand;
import momzzangseven.mztkbe.modules.user.application.dto.UserInfo;
import momzzangseven.mztkbe.modules.user.application.port.out.SaveUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateUserService 단위 테스트")
class CreateUserServiceTest {

  @Mock private SaveUserPort saveUserPort;

  private CreateUserService service;

  @BeforeEach
  void setUp() {
    service = new CreateUserService(saveUserPort);
  }

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("[M-47] 정상 생성 → UserInfo 반환")
    void createUser_validCommand_returnsUserInfo() {
      // given
      CreateUserCommand command =
          new CreateUserCommand("test@example.com", "tester", "https://img.com/1.png", "USER");
      given(saveUserPort.saveUser(any(User.class)))
          .willAnswer(
              invocation -> {
                User user = invocation.getArgument(0);
                return user.toBuilder().id(1L).build();
              });

      // when
      UserInfo result = service.createUser(command);

      // then
      assertThat(result.id()).isEqualTo(1L);
      assertThat(result.email()).isEqualTo("test@example.com");
      assertThat(result.role()).isEqualTo(UserRole.USER);

      ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
      verify(saveUserPort).saveUser(captor.capture());
      User savedUser = captor.getValue();
      assertThat(savedUser.getRole()).isEqualTo(UserRole.USER);
      assertThat(savedUser.getProfileImageUrl()).isEqualTo("https://img.com/1.png");
    }

    @Test
    @DisplayName("[M-48] String role \"TRAINER\" → UserRole.TRAINER 변환")
    void createUser_trainerRole_convertsCorrectly() {
      // given
      CreateUserCommand command =
          new CreateUserCommand("trainer@example.com", "trainer", null, "TRAINER");
      given(saveUserPort.saveUser(any(User.class)))
          .willAnswer(
              invocation -> {
                User user = invocation.getArgument(0);
                return user.toBuilder().id(2L).build();
              });

      // when
      UserInfo result = service.createUser(command);

      // then
      assertThat(result.role()).isEqualTo(UserRole.TRAINER);

      ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
      verify(saveUserPort).saveUser(captor.capture());
      assertThat(captor.getValue().getRole()).isEqualTo(UserRole.TRAINER);
    }

    @Test
    @DisplayName("[M-50] profileImageUrl이 null이어도 정상 생성")
    void createUser_nullProfileImage_createsSuccessfully() {
      // given
      CreateUserCommand command = new CreateUserCommand("test@example.com", "tester", null, "USER");
      given(saveUserPort.saveUser(any(User.class)))
          .willAnswer(
              invocation -> {
                User user = invocation.getArgument(0);
                return user.toBuilder().id(3L).build();
              });

      // when
      UserInfo result = service.createUser(command);

      // then
      assertThat(result.profileImageUrl()).isNull();
      assertThat(result.id()).isEqualTo(3L);
    }
  }

  @Nested
  @DisplayName("실패 케이스")
  class FailureCases {

    @Test
    @DisplayName("[M-49] 잘못된 role String → IllegalArgumentException")
    void createUser_invalidRole_throwsException() {
      // given
      CreateUserCommand command =
          new CreateUserCommand("test@example.com", "tester", null, "INVALID_ROLE");

      // when & then
      assertThatThrownBy(() -> service.createUser(command))
          .isInstanceOf(IllegalArgumentException.class);

      verify(saveUserPort, never()).saveUser(any());
    }
  }
}
