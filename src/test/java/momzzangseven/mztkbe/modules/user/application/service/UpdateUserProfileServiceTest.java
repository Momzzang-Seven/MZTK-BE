package momzzangseven.mztkbe.modules.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.UserNotFoundException;
import momzzangseven.mztkbe.modules.user.application.dto.UpdateUserProfileCommand;
import momzzangseven.mztkbe.modules.user.application.dto.UserInfo;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.application.port.out.SaveUserPort;
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
@DisplayName("UpdateUserProfileService 단위 테스트")
class UpdateUserProfileServiceTest {

  @Mock private LoadUserPort loadUserPort;
  @Mock private SaveUserPort saveUserPort;

  private UpdateUserProfileService service;

  private User existingUser;

  @BeforeEach
  void setUp() {
    service = new UpdateUserProfileService(loadUserPort, saveUserPort);

    Instant now = Instant.now();
    existingUser =
        User.builder()
            .id(1L)
            .email("test@example.com")
            .nickname("oldNick")
            .profileImageUrl("https://img.example.com/old.png")
            .role(UserRole.USER)
            .createdAt(now)
            .updatedAt(now)
            .build();
  }

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("[M-54] 정상 수정 → 변경된 UserInfo 반환")
    void updateProfile_validCommand_returnsUpdatedUserInfo() {
      // given
      given(loadUserPort.loadUserById(1L)).willReturn(Optional.of(existingUser));
      given(saveUserPort.saveUser(any(User.class)))
          .willAnswer(invocation -> invocation.getArgument(0));

      UpdateUserProfileCommand command =
          new UpdateUserProfileCommand(1L, "newNick", "https://img.example.com/new.png");

      // when
      UserInfo result = service.updateProfile(command);

      // then
      assertThat(result.nickname()).isEqualTo("newNick");
      assertThat(result.profileImageUrl()).isEqualTo("https://img.example.com/new.png");
      assertThat(result.id()).isEqualTo(1L);
      verify(loadUserPort).loadUserById(1L);
      verify(saveUserPort).saveUser(any(User.class));
    }

    @Test
    @DisplayName("[M-56] nickname만 변경, profileImageUrl은 기존값 유지 가능")
    void updateProfile_keepExistingImage_returnsCorrectInfo() {
      // given
      given(loadUserPort.loadUserById(1L)).willReturn(Optional.of(existingUser));
      given(saveUserPort.saveUser(any(User.class)))
          .willAnswer(invocation -> invocation.getArgument(0));

      UpdateUserProfileCommand command =
          new UpdateUserProfileCommand(1L, "newNick", "https://img.example.com/old.png");

      // when
      UserInfo result = service.updateProfile(command);

      // then
      assertThat(result.nickname()).isEqualTo("newNick");
      assertThat(result.profileImageUrl()).isEqualTo("https://img.example.com/old.png");
    }
  }

  @Nested
  @DisplayName("실패 케이스")
  class FailureCases {

    @Test
    @DisplayName("[M-55] 미존재 사용자 → UserNotFoundException")
    void updateProfile_nonExistingUser_throwsException() {
      // given
      given(loadUserPort.loadUserById(999L)).willReturn(Optional.empty());

      UpdateUserProfileCommand command = new UpdateUserProfileCommand(999L, "newNick", null);

      // when & then
      assertThatThrownBy(() -> service.updateProfile(command))
          .isInstanceOf(UserNotFoundException.class);

      verify(saveUserPort, never()).saveUser(any());
    }
  }
}
