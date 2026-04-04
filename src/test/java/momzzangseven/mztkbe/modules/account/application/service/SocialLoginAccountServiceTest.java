package momzzangseven.mztkbe.modules.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
              AuthProvider.KAKAO, "kakao123", "kakao@example.com", "nick", null);

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
                      AuthProvider.KAKAO, "kakao123", "kakao@example.com", "nick", null))
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
              AuthProvider.KAKAO, "kakao123", "kakao@example.com", "kakaoUser", null);

      assertThat(outcome.isNewUser()).isTrue();
      assertThat(outcome.userSnapshot().userId()).isEqualTo(1L);
      verify(createAccountUserPort).createUser("kakao@example.com", "kakaoUser", null, "USER");
    }
  }

  @Nested
  @DisplayName("Email conflict")
  class EmailConflict {

    @Test
    @DisplayName("should throw InvalidCredentialsException when email already exists")
    void shouldThrowForEmailConflict() {
      when(loadUserAccountPort.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google123"))
          .thenReturn(Optional.empty());
      when(loadAccountUserInfoPort.existsByEmail("existing@example.com")).thenReturn(true);

      assertThatThrownBy(
              () ->
                  service.loginOrRegister(
                      AuthProvider.GOOGLE, "google123", "existing@example.com", "nick", null))
          .isInstanceOf(InvalidCredentialsException.class);
    }
  }
}
