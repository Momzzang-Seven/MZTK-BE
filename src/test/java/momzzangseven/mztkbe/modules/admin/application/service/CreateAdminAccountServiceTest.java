package momzzangseven.mztkbe.modules.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import momzzangseven.mztkbe.global.error.admin.AdminCredentialGenerationException;
import momzzangseven.mztkbe.modules.admin.application.dto.CreateAdminAccountResult;
import momzzangseven.mztkbe.modules.admin.application.port.out.AdminPasswordEncoderPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.CreateAdminUserPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.GenerateCredentialPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.LoadAdminAccountPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.SaveAdminAccountPort;
import momzzangseven.mztkbe.modules.admin.domain.model.AdminAccount;
import momzzangseven.mztkbe.modules.admin.domain.vo.GeneratedAdminCredentials;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateAdminAccountService 단위 테스트")
class CreateAdminAccountServiceTest {

  @Mock private GenerateCredentialPort generateCredentialPort;
  @Mock private CreateAdminUserPort createAdminUserPort;
  @Mock private AdminPasswordEncoderPort adminPasswordEncoderPort;
  @Mock private SaveAdminAccountPort saveAdminAccountPort;
  @Mock private LoadAdminAccountPort loadAdminAccountPort;

  @InjectMocks private CreateAdminAccountService service;

  private static final String LOGIN_ID = "12345678";
  private static final String PLAINTEXT = "Abcdefghij1234567890!";
  private static final String PASSWORD_HASH = "$2a$10$encoded";

  private AdminAccount buildSavedAccount(Long userId, String loginId, Long createdBy) {
    return AdminAccount.builder()
        .id(1L)
        .userId(userId)
        .loginId(loginId)
        .passwordHash(PASSWORD_HASH)
        .createdBy(createdBy)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .passwordLastRotatedAt(Instant.now())
        .build();
  }

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("[M-126] execute creates admin account with generated unique credentials")
    void execute_validInput_createsAdminAccount() {
      // given
      given(generateCredentialPort.generate())
          .willReturn(new GeneratedAdminCredentials(LOGIN_ID, PLAINTEXT));
      given(loadAdminAccountPort.existsByLoginId(LOGIN_ID)).willReturn(false);
      given(createAdminUserPort.createAdmin(any(), any(), eq(UserRole.ADMIN_GENERATED)))
          .willReturn(100L);
      given(adminPasswordEncoderPort.encode(PLAINTEXT)).willReturn(PASSWORD_HASH);
      given(saveAdminAccountPort.save(any(AdminAccount.class)))
          .willReturn(buildSavedAccount(100L, LOGIN_ID, 1L));

      // when
      CreateAdminAccountResult result = service.execute(1L);

      // then
      assertThat(result.userId()).isEqualTo(100L);
      assertThat(result.loginId()).isEqualTo(LOGIN_ID);
      assertThat(result.plaintext()).isEqualTo(PLAINTEXT);

      verify(createAdminUserPort)
          .createAdmin(
              eq("admin-12345678@internal.mztk.local"),
              eq("Admin-12345678"),
              eq(UserRole.ADMIN_GENERATED));
      verify(adminPasswordEncoderPort).encode(PLAINTEXT);

      ArgumentCaptor<AdminAccount> captor = ArgumentCaptor.forClass(AdminAccount.class);
      verify(saveAdminAccountPort).save(captor.capture());
      assertThat(captor.getValue().getCreatedBy()).isEqualTo(1L);
    }

    @Test
    @DisplayName("[M-129] execute passes operatorUserId as createdBy to AdminAccount")
    void execute_operatorUserId_passedAsCreatedBy() {
      // given
      given(generateCredentialPort.generate())
          .willReturn(new GeneratedAdminCredentials(LOGIN_ID, PLAINTEXT));
      given(loadAdminAccountPort.existsByLoginId(LOGIN_ID)).willReturn(false);
      given(createAdminUserPort.createAdmin(any(), any(), eq(UserRole.ADMIN_GENERATED)))
          .willReturn(100L);
      given(adminPasswordEncoderPort.encode(PLAINTEXT)).willReturn(PASSWORD_HASH);
      given(saveAdminAccountPort.save(any(AdminAccount.class)))
          .willReturn(buildSavedAccount(100L, LOGIN_ID, 42L));

      // when
      service.execute(42L);

      // then
      ArgumentCaptor<AdminAccount> captor = ArgumentCaptor.forClass(AdminAccount.class);
      verify(saveAdminAccountPort).save(captor.capture());
      assertThat(captor.getValue().getCreatedBy()).isEqualTo(42L);
    }
  }

  @Nested
  @DisplayName("재시도 및 실패 케이스")
  class RetryAndFailureCases {

    @Test
    @DisplayName("[M-127] execute retries credential generation when loginId already exists")
    void execute_loginIdCollision_retriesAndSucceeds() {
      // given
      GeneratedAdminCredentials first = new GeneratedAdminCredentials("11111111", PLAINTEXT);
      GeneratedAdminCredentials second = new GeneratedAdminCredentials("22222222", PLAINTEXT);
      given(generateCredentialPort.generate()).willReturn(first, second);
      given(loadAdminAccountPort.existsByLoginId("11111111")).willReturn(true);
      given(loadAdminAccountPort.existsByLoginId("22222222")).willReturn(false);
      given(createAdminUserPort.createAdmin(any(), any(), eq(UserRole.ADMIN_GENERATED)))
          .willReturn(100L);
      given(adminPasswordEncoderPort.encode(PLAINTEXT)).willReturn(PASSWORD_HASH);
      given(saveAdminAccountPort.save(any(AdminAccount.class)))
          .willReturn(buildSavedAccount(100L, "22222222", 1L));

      // when
      CreateAdminAccountResult result = service.execute(1L);

      // then
      assertThat(result.loginId()).isEqualTo("22222222");
      verify(generateCredentialPort, times(2)).generate();
      verify(loadAdminAccountPort, times(2)).existsByLoginId(any());
    }

    @Test
    @DisplayName(
        "[M-128] execute throws AdminCredentialGenerationException after 5 failed uniqueness"
            + " checks")
    void execute_allRetriesFail_throwsAdminCredentialGenerationException() {
      // given
      given(generateCredentialPort.generate())
          .willReturn(new GeneratedAdminCredentials("99999999", PLAINTEXT));
      given(loadAdminAccountPort.existsByLoginId("99999999")).willReturn(true);

      // when & then
      assertThatThrownBy(() -> service.execute(1L))
          .isInstanceOf(AdminCredentialGenerationException.class)
          .hasMessageContaining("5 attempts");

      verify(generateCredentialPort, times(5)).generate();
      verify(createAdminUserPort, never()).createAdmin(any(), any(), any());
      verify(saveAdminAccountPort, never()).save(any());
    }
  }
}
