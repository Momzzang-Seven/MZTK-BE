package momzzangseven.mztkbe.modules.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.InvalidCredentialsException;
import momzzangseven.mztkbe.global.error.user.UserWithdrawnException;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.application.port.in.SocialLoginOutcome;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.application.port.out.SaveUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import momzzangseven.mztkbe.modules.user.domain.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService unit test")
class UserServiceTest {

  @Mock private LoadUserPort loadUserPort;
  @Mock private SaveUserPort saveUserPort;

  private UserService service;

  @BeforeEach
  void setUp() {
    service = new UserService(loadUserPort, saveUserPort);
  }

  @Test
  @DisplayName("loginOrRegisterSocial rejects blank provider")
  void loginOrRegisterSocial_withBlankProvider_throws() {
    assertThatThrownBy(() -> service.loginOrRegisterSocial(" ", "pid", "a@b.com", "nick", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("provider is required");
  }

  @Test
  @DisplayName("loginOrRegisterSocial rejects null provider")
  void loginOrRegisterSocial_withNullProvider_throws() {
    assertThatThrownBy(() -> service.loginOrRegisterSocial(null, "pid", "a@b.com", "nick", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("provider is required");
  }

  @Test
  @DisplayName("loginOrRegisterSocial rejects unsupported provider")
  void loginOrRegisterSocial_withUnsupportedProvider_throws() {
    assertThatThrownBy(() -> service.loginOrRegisterSocial("NAVER", "pid", "a@b.com", "nick", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported social provider: NAVER");
  }

  @Test
  @DisplayName("loginOrRegisterSocial rejects blank providerUserId")
  void loginOrRegisterSocial_withBlankProviderUserId_throws() {
    assertThatThrownBy(() -> service.loginOrRegisterSocial("KAKAO", " ", "a@b.com", "nick", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("providerUserId is required");
  }

  @Test
  @DisplayName("loginOrRegisterSocial rejects null providerUserId")
  void loginOrRegisterSocial_withNullProviderUserId_throws() {
    assertThatThrownBy(() -> service.loginOrRegisterSocial("KAKAO", null, "a@b.com", "nick", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("providerUserId is required");
  }

  @Test
  @DisplayName("loginOrRegisterSocial rejects blank email")
  void loginOrRegisterSocial_withBlankEmail_throws() {
    assertThatThrownBy(() -> service.loginOrRegisterSocial("KAKAO", "pid", " ", "nick", null))
        .isInstanceOf(InvalidCredentialsException.class)
        .hasMessage("Invalid social login");
  }

  @Test
  @DisplayName("loginOrRegisterSocial rejects null email")
  void loginOrRegisterSocial_withNullEmail_throws() {
    assertThatThrownBy(() -> service.loginOrRegisterSocial("KAKAO", "pid", null, "nick", null))
        .isInstanceOf(InvalidCredentialsException.class)
        .hasMessage("Invalid social login");
  }

  @Test
  @DisplayName("loginOrRegisterSocial returns existing user and updates login timestamp")
  void loginOrRegisterSocial_withExistingProviderUser_returnsExisting() {
    LocalDateTime oldLoginAt = LocalDateTime.of(2026, 2, 20, 12, 0);
    User existing =
        User.builder()
            .id(1L)
            .email("existing@example.com")
            .nickname("existing")
            .authProvider(AuthProvider.KAKAO)
            .providerUserId("kakao-1")
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .lastLoginAt(oldLoginAt)
            .createdAt(oldLoginAt.minusDays(30))
            .updatedAt(oldLoginAt.minusDays(1))
            .build();

    when(loadUserPort.findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-1"))
        .thenReturn(Optional.of(existing));

    SocialLoginOutcome outcome =
        service.loginOrRegisterSocial(
            "KAKAO", "kakao-1", "existing@example.com", "existing", "profile");

    assertThat(outcome.isNewUser()).isFalse();
    assertThat(outcome.user()).isSameAs(existing);
    assertThat(existing.getLastLoginAt()).isAfter(oldLoginAt);
    verify(saveUserPort).saveUser(existing);
  }

  @Test
  @DisplayName("loginOrRegisterSocial blocks withdrawn account by provider id")
  void loginOrRegisterSocial_withWithdrawnProviderAccount_throws() {
    User withdrawn = baseUser(10L, "withdrawn@example.com", AuthProvider.KAKAO, UserStatus.DELETED);

    when(loadUserPort.findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-withdrawn"))
        .thenReturn(Optional.empty());
    when(loadUserPort.findDeletedByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-withdrawn"))
        .thenReturn(Optional.of(withdrawn));

    assertThatThrownBy(
            () ->
                service.loginOrRegisterSocial(
                    "KAKAO", "kakao-withdrawn", "withdrawn@example.com", "nick", null))
        .isInstanceOf(UserWithdrawnException.class);

    verify(loadUserPort, never()).loadUserByEmail("withdrawn@example.com");
    verify(saveUserPort, never()).saveUser(any(User.class));
  }

  @Test
  @DisplayName("loginOrRegisterSocial rejects email collision even with same provider")
  void loginOrRegisterSocial_withEmailCollision_throwsInvalidCredentials() {
    User byEmail = baseUser(3L, "collision@example.com", AuthProvider.KAKAO, UserStatus.ACTIVE);

    when(loadUserPort.findByProviderAndProviderUserId(AuthProvider.KAKAO, "different-id"))
        .thenReturn(Optional.empty());
    when(loadUserPort.findDeletedByProviderAndProviderUserId(AuthProvider.KAKAO, "different-id"))
        .thenReturn(Optional.empty());
    when(loadUserPort.loadUserByEmail("collision@example.com")).thenReturn(Optional.of(byEmail));

    assertThatThrownBy(
            () ->
                service.loginOrRegisterSocial(
                    "KAKAO", "different-id", "collision@example.com", "nick", null))
        .isInstanceOf(InvalidCredentialsException.class)
        .hasMessage("Invalid social login");

    verify(loadUserPort, never()).loadDeletedUserByEmail("collision@example.com");
    verify(saveUserPort, never()).saveUser(any(User.class));
  }

  @Test
  @DisplayName("loginOrRegisterSocial rejects email collision with different provider")
  void loginOrRegisterSocial_withEmailCollisionDifferentProvider_throwsInvalidCredentials() {
    User byEmail = baseUser(4L, "collision@example.com", AuthProvider.GOOGLE, UserStatus.ACTIVE);

    when(loadUserPort.findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-id"))
        .thenReturn(Optional.empty());
    when(loadUserPort.findDeletedByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-id"))
        .thenReturn(Optional.empty());
    when(loadUserPort.loadUserByEmail("collision@example.com")).thenReturn(Optional.of(byEmail));

    assertThatThrownBy(
            () ->
                service.loginOrRegisterSocial(
                    "KAKAO", "kakao-id", "collision@example.com", "nick", null))
        .isInstanceOf(InvalidCredentialsException.class)
        .hasMessage("Invalid social login");

    verify(loadUserPort, never()).loadDeletedUserByEmail("collision@example.com");
    verify(saveUserPort, never()).saveUser(any(User.class));
  }

  @Test
  @DisplayName("loginOrRegisterSocial creates new social user with fallback nickname")
  void loginOrRegisterSocial_withNewSocialUser_createsUser() {
    when(loadUserPort.findByProviderAndProviderUserId(AuthProvider.KAKAO, "new-kakao-id"))
        .thenReturn(Optional.empty());
    when(loadUserPort.findDeletedByProviderAndProviderUserId(AuthProvider.KAKAO, "new-kakao-id"))
        .thenReturn(Optional.empty());
    when(loadUserPort.loadUserByEmail("new@example.com")).thenReturn(Optional.empty());
    when(loadUserPort.loadDeletedUserByEmail("new@example.com")).thenReturn(Optional.empty());
    when(saveUserPort.saveUser(any(User.class)))
        .thenAnswer(
            invocation -> invocation.getArgument(0, User.class).toBuilder().id(777L).build());

    SocialLoginOutcome outcome =
        service.loginOrRegisterSocial("KAKAO", "new-kakao-id", "new@example.com", " ", null);

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(saveUserPort).saveUser(userCaptor.capture());
    User created = userCaptor.getValue();

    assertThat(created.getAuthProvider()).isEqualTo(AuthProvider.KAKAO);
    assertThat(created.getProviderUserId()).isEqualTo("new-kakao-id");
    assertThat(created.getEmail()).isEqualTo("new@example.com");
    assertThat(created.getNickname()).startsWith("kakao_");

    assertThat(outcome.isNewUser()).isTrue();
    assertThat(outcome.user().getId()).isEqualTo(777L);
  }

  @Test
  @DisplayName("loginOrRegisterSocial creates GOOGLE social user with provided nickname")
  void loginOrRegisterSocial_withGoogleNewUser_createsGoogleUser() {
    when(loadUserPort.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-id"))
        .thenReturn(Optional.empty());
    when(loadUserPort.findDeletedByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-id"))
        .thenReturn(Optional.empty());
    when(loadUserPort.loadUserByEmail("google@example.com")).thenReturn(Optional.empty());
    when(loadUserPort.loadDeletedUserByEmail("google@example.com")).thenReturn(Optional.empty());
    when(saveUserPort.saveUser(any(User.class)))
        .thenAnswer(
            invocation -> invocation.getArgument(0, User.class).toBuilder().id(778L).build());

    SocialLoginOutcome outcome =
        service.loginOrRegisterSocial(
            "GOOGLE", "google-id", "google@example.com", "googleNick", "profile");

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(saveUserPort).saveUser(userCaptor.capture());
    User created = userCaptor.getValue();

    assertThat(created.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
    assertThat(created.getNickname()).isEqualTo("googleNick");
    assertThat(outcome.isNewUser()).isTrue();
    assertThat(outcome.user().getId()).isEqualTo(778L);
  }

  @Test
  @DisplayName("loginOrRegisterSocial blocks withdrawn account by email")
  void loginOrRegisterSocial_withWithdrawnEmail_throws() {
    User withdrawnByEmail =
        baseUser(11L, "withdrawn@example.com", AuthProvider.KAKAO, UserStatus.DELETED);

    when(loadUserPort.findByProviderAndProviderUserId(AuthProvider.KAKAO, "new-id"))
        .thenReturn(Optional.empty());
    when(loadUserPort.findDeletedByProviderAndProviderUserId(AuthProvider.KAKAO, "new-id"))
        .thenReturn(Optional.empty());
    when(loadUserPort.loadUserByEmail("withdrawn@example.com")).thenReturn(Optional.empty());
    when(loadUserPort.loadDeletedUserByEmail("withdrawn@example.com"))
        .thenReturn(Optional.of(withdrawnByEmail));

    assertThatThrownBy(
            () ->
                service.loginOrRegisterSocial(
                    "KAKAO", "new-id", "withdrawn@example.com", "nick", null))
        .isInstanceOf(UserWithdrawnException.class);

    verify(saveUserPort, never()).saveUser(any(User.class));
  }

  private User baseUser(Long id, String email, AuthProvider provider, UserStatus status) {
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 15, 0);
    return User.builder()
        .id(id)
        .email(email)
        .nickname("nick")
        .authProvider(provider)
        .providerUserId("provider-user-id")
        .role(UserRole.USER)
        .status(status)
        .createdAt(now.minusDays(30))
        .updatedAt(now.minusDays(1))
        .build();
  }
}
