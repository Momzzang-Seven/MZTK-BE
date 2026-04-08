package momzzangseven.mztkbe.modules.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.InvalidCredentialsException;
import momzzangseven.mztkbe.global.error.user.UserWithdrawnException;
import momzzangseven.mztkbe.modules.account.application.dto.AccountUserSnapshot;
import momzzangseven.mztkbe.modules.account.application.dto.SocialLoginAccountOutcome;
import momzzangseven.mztkbe.modules.account.application.port.out.CreateAccountUserPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadAccountUserInfoPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserAccountPort;
import momzzangseven.mztkbe.modules.account.application.port.out.SaveUserAccountPort;
import momzzangseven.mztkbe.modules.account.domain.model.UserAccount;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("SocialLoginAccountService")
@ExtendWith(MockitoExtension.class)
class SocialLoginAccountServiceTest {

  @Mock private LoadUserAccountPort loadUserAccountPort;
  @Mock private SaveUserAccountPort saveUserAccountPort;
  @Mock private LoadAccountUserInfoPort loadAccountUserInfoPort;
  @Mock private CreateAccountUserPort createAccountUserPort;

  @InjectMocks private SocialLoginAccountService service;

  private final AccountUserSnapshot snapshot =
      new AccountUserSnapshot(1L, "kakao@example.com", "kakaoUser", null, "USER");

  @Nested
  @DisplayName("Existing active account")
  class ExistingActive {

    @Test
    @DisplayName("should return existing user and update last login")
    void shouldReturnExisting() {
      UserAccount account =
          UserAccount.builder()
              .id(1L)
              .userId(1L)
              .provider(AuthProvider.KAKAO)
              .providerUserId("kakao123")
              .status(AccountStatus.ACTIVE)
              .build();

      when(loadUserAccountPort.findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao123"))
          .thenReturn(Optional.of(account));
      when(saveUserAccountPort.save(any(UserAccount.class))).thenReturn(account);
      when(loadAccountUserInfoPort.findById(1L)).thenReturn(Optional.of(snapshot));

      SocialLoginAccountOutcome outcome =
          service.loginOrRegister(
              AuthProvider.KAKAO, "kakao123", "kakao@example.com", "nick", null, null);

      assertThat(outcome.isNewUser()).isFalse();
      assertThat(outcome.userSnapshot().userId()).isEqualTo(1L);
      verify(saveUserAccountPort).save(any(UserAccount.class));
    }
  }

  @Nested
  @DisplayName("Deleted account")
  class DeletedAccount {

    @Test
    @DisplayName("should throw UserWithdrawnException for deleted account by provider")
    void shouldThrowForDeletedByProvider() {
      UserAccount deleted =
          UserAccount.builder()
              .id(1L)
              .userId(1L)
              .provider(AuthProvider.KAKAO)
              .providerUserId("kakao123")
              .status(AccountStatus.DELETED)
              .build();

      when(loadUserAccountPort.findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao123"))
          .thenReturn(Optional.of(deleted));

      assertThatThrownBy(
              () ->
                  service.loginOrRegister(
                      AuthProvider.KAKAO, "kakao123", "kakao@example.com", "nick", null, null))
          .isInstanceOf(UserWithdrawnException.class);
    }
  }

  @Nested
  @DisplayName("New user")
  class NewUser {

    @Test
    @DisplayName("should create new user and account")
    void shouldCreateNew() {
      when(loadUserAccountPort.findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao123"))
          .thenReturn(Optional.empty());
      when(loadAccountUserInfoPort.existsByEmail("kakao@example.com")).thenReturn(false);
      when(loadUserAccountPort.findDeletedByEmail("kakao@example.com"))
          .thenReturn(Optional.empty());
      when(createAccountUserPort.createUser(anyString(), anyString(), any(), anyString()))
          .thenReturn(snapshot);
      when(saveUserAccountPort.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

      SocialLoginAccountOutcome outcome =
          service.loginOrRegister(
              AuthProvider.KAKAO, "kakao123", "kakao@example.com", "kakaoUser", null, null);

      assertThat(outcome.isNewUser()).isTrue();
      assertThat(outcome.userSnapshot().userId()).isEqualTo(1L);
      verify(createAccountUserPort).createUser("kakao@example.com", "kakaoUser", null, "USER");
    }

    @Test
    @DisplayName("[M-5] TRAINER role 명시 시 TRAINER role로 신규 유저 생성")
    void shouldCreateNewWithTrainerRole() {
      AccountUserSnapshot trainerSnapshot =
          new AccountUserSnapshot(2L, "kakao@example.com", "kakaoUser", null, "TRAINER");

      when(loadUserAccountPort.findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao123"))
          .thenReturn(Optional.empty());
      when(loadUserAccountPort.findDeletedByEmail("kakao@example.com"))
          .thenReturn(Optional.empty());
      when(loadAccountUserInfoPort.existsByEmail("kakao@example.com")).thenReturn(false);
      when(createAccountUserPort.createUser(anyString(), anyString(), any(), anyString()))
          .thenReturn(trainerSnapshot);
      when(saveUserAccountPort.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

      SocialLoginAccountOutcome outcome =
          service.loginOrRegister(
              AuthProvider.KAKAO, "kakao123", "kakao@example.com", "kakaoUser", null, "TRAINER");

      assertThat(outcome.isNewUser()).isTrue();
      verify(createAccountUserPort).createUser("kakao@example.com", "kakaoUser", null, "TRAINER");
    }

    @Test
    @DisplayName("[M-6] role이 null이면 기본값 USER로 신규 소셜 유저 생성")
    void shouldCreateNewWithDefaultUserRoleWhenNull() {
      when(loadUserAccountPort.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google123"))
          .thenReturn(Optional.empty());
      when(loadUserAccountPort.findDeletedByEmail("google@example.com"))
          .thenReturn(Optional.empty());
      when(loadAccountUserInfoPort.existsByEmail("google@example.com")).thenReturn(false);
      when(createAccountUserPort.createUser(anyString(), anyString(), any(), anyString()))
          .thenReturn(new AccountUserSnapshot(3L, "google@example.com", "nick", null, "USER"));
      when(saveUserAccountPort.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

      SocialLoginAccountOutcome outcome =
          service.loginOrRegister(
              AuthProvider.GOOGLE, "google123", "google@example.com", "nick", null, null);

      assertThat(outcome.isNewUser()).isTrue();
      verify(createAccountUserPort).createUser("google@example.com", "nick", null, "USER");
    }

    @Test
    @DisplayName("[M-8] USER role 명시 시 USER role로 신규 유저 생성")
    void shouldCreateNewWithExplicitUserRole() {
      when(loadUserAccountPort.findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao456"))
          .thenReturn(Optional.empty());
      when(loadUserAccountPort.findDeletedByEmail("user2@kakao.com")).thenReturn(Optional.empty());
      when(loadAccountUserInfoPort.existsByEmail("user2@kakao.com")).thenReturn(false);
      when(createAccountUserPort.createUser(anyString(), anyString(), any(), anyString()))
          .thenReturn(new AccountUserSnapshot(4L, "user2@kakao.com", "nick2", null, "USER"));
      when(saveUserAccountPort.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

      SocialLoginAccountOutcome outcome =
          service.loginOrRegister(
              AuthProvider.KAKAO, "kakao456", "user2@kakao.com", "nick2", null, "USER");

      assertThat(outcome.isNewUser()).isTrue();
      verify(createAccountUserPort).createUser("user2@kakao.com", "nick2", null, "USER");
    }
  }

  @Nested
  @DisplayName("Existing account ignores role")
  class ExistingAccountIgnoresRole {

    @Test
    @DisplayName("[M-7] 기존 유저 로그인 시 role 파라미터 무시")
    void shouldIgnoreRoleForExistingUser() {
      AccountUserSnapshot existingSnapshot =
          new AccountUserSnapshot(1L, "kakao@example.com", "kakaoUser", null, "USER");
      UserAccount account =
          UserAccount.builder()
              .id(1L)
              .userId(1L)
              .provider(AuthProvider.KAKAO)
              .providerUserId("kakao123")
              .status(AccountStatus.ACTIVE)
              .build();

      when(loadUserAccountPort.findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao123"))
          .thenReturn(Optional.of(account));
      when(saveUserAccountPort.save(any(UserAccount.class))).thenReturn(account);
      when(loadAccountUserInfoPort.findById(1L)).thenReturn(Optional.of(existingSnapshot));

      SocialLoginAccountOutcome outcome =
          service.loginOrRegister(
              AuthProvider.KAKAO, "kakao123", "kakao@example.com", "nick", null, "TRAINER");

      assertThat(outcome.isNewUser()).isFalse();
      verify(createAccountUserPort, never()).createUser(any(), any(), any(), any());
    }
  }

  @Nested
  @DisplayName("Email conflict")
  class EmailConflict {

    @Test
    @DisplayName("should throw InvalidCredentialsException when email already exists as active")
    void shouldThrowForEmailConflict() {
      when(loadUserAccountPort.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google123"))
          .thenReturn(Optional.empty());
      when(loadUserAccountPort.findDeletedByEmail("existing@example.com"))
          .thenReturn(Optional.empty());
      when(loadAccountUserInfoPort.existsByEmail("existing@example.com")).thenReturn(true);

      assertThatThrownBy(
              () ->
                  service.loginOrRegister(
                      AuthProvider.GOOGLE, "google123", "existing@example.com", "nick", null, null))
          .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("should throw UserWithdrawnException when email belongs to a withdrawn account")
    void shouldThrowForWithdrawnEmail() {
      UserAccount deletedAccount =
          UserAccount.builder()
              .id(2L)
              .userId(2L)
              .provider(AuthProvider.KAKAO)
              .providerUserId("kakao999")
              .status(AccountStatus.DELETED)
              .build();

      when(loadUserAccountPort.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google456"))
          .thenReturn(Optional.empty());
      when(loadUserAccountPort.findDeletedByEmail("withdrawn@example.com"))
          .thenReturn(Optional.of(deletedAccount));

      assertThatThrownBy(
              () ->
                  service.loginOrRegister(
                      AuthProvider.GOOGLE,
                      "google456",
                      "withdrawn@example.com",
                      "nick",
                      null,
                      null))
          .isInstanceOf(UserWithdrawnException.class);
    }
  }
}
