package momzzangseven.mztkbe.modules.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import momzzangseven.mztkbe.global.error.admin.AdminCredentialGenerationException;
import momzzangseven.mztkbe.modules.admin.application.dto.SeedBootstrapOutcome;
import momzzangseven.mztkbe.modules.admin.application.port.out.BootstrapDeliveryPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.CountActiveAdminAccountsPort;
import momzzangseven.mztkbe.modules.admin.domain.vo.GeneratedAdminCredentials;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BootstrapSeedAdminsService 단위 테스트")
class BootstrapSeedAdminsServiceTest {

  @Mock private CountActiveAdminAccountsPort countActiveAdminAccountsPort;
  @Mock private SeedProvisioner seedProvisioner;
  @Mock private BootstrapDeliveryPort bootstrapDeliveryPort;

  @InjectMocks private BootstrapSeedAdminsService service;

  private static final String DELIVERY_TARGET = "mztk/admin/bootstrap-delivery";

  private List<GeneratedAdminCredentials> buildCredentials(int count) {
    return java.util.stream.IntStream.rangeClosed(1, count)
        .mapToObj(i -> new GeneratedAdminCredentials("1000000" + i, "Abcdefghij123456789!"))
        .toList();
  }

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("[M-156] execute skips provisioning when 2 or more active admins exist")
    void execute_twoActiveAdmins_skipsProvisioning() {
      // given
      given(countActiveAdminAccountsPort.countActive()).willReturn(2L);

      // when
      SeedBootstrapOutcome outcome = service.execute();

      // then
      assertThat(outcome.created()).isFalse();
      assertThat(outcome.seedCount()).isEqualTo(2);
      assertThat(outcome.deliveredVia()).isNull();
      verify(seedProvisioner, never()).provision(anyInt(), any());
      verify(bootstrapDeliveryPort, never()).deliver(any());
    }

    @Test
    @DisplayName("[M-157] execute skips provisioning when more than 2 active admins exist")
    void execute_fiveActiveAdmins_skipsProvisioning() {
      // given
      given(countActiveAdminAccountsPort.countActive()).willReturn(5L);

      // when
      SeedBootstrapOutcome outcome = service.execute();

      // then
      assertThat(outcome.created()).isFalse();
      assertThat(outcome.seedCount()).isEqualTo(5);
      assertThat(outcome.deliveredVia()).isNull();
      verify(seedProvisioner, never()).provision(anyInt(), any());
      verify(bootstrapDeliveryPort, never()).deliver(any());
    }

    @Test
    @DisplayName("[M-158] execute provisions 2 admins when zero active admins exist")
    void execute_zeroActiveAdmins_provisionsTwo() {
      // given
      given(countActiveAdminAccountsPort.countActive()).willReturn(0L);
      List<GeneratedAdminCredentials> credentials = buildCredentials(2);
      given(seedProvisioner.provision(2, UserRole.ADMIN_SEED)).willReturn(credentials);

      // when
      SeedBootstrapOutcome outcome = service.execute();

      // then
      assertThat(outcome.created()).isTrue();
      assertThat(outcome.seedCount()).isEqualTo(2);
      assertThat(outcome.deliveredVia()).isEqualTo(DELIVERY_TARGET);
      verify(seedProvisioner).provision(2, UserRole.ADMIN_SEED);
      verify(bootstrapDeliveryPort).deliver(credentials);
    }

    @Test
    @DisplayName("[M-159] execute provisions 1 admin when 1 active admin exists (deficit = 1)")
    void execute_oneActiveAdmin_provisionsOne() {
      // given
      given(countActiveAdminAccountsPort.countActive()).willReturn(1L);
      List<GeneratedAdminCredentials> credentials = buildCredentials(1);
      given(seedProvisioner.provision(1, UserRole.ADMIN_SEED)).willReturn(credentials);

      // when
      SeedBootstrapOutcome outcome = service.execute();

      // then
      assertThat(outcome.created()).isTrue();
      assertThat(outcome.seedCount()).isEqualTo(1);
      assertThat(outcome.deliveredVia()).isEqualTo(DELIVERY_TARGET);
      verify(seedProvisioner).provision(1, UserRole.ADMIN_SEED);
      verify(bootstrapDeliveryPort).deliver(credentials);
    }

    @Test
    @DisplayName("[M-160] execute delivers credentials via BootstrapDeliveryPort")
    void execute_provisionsAdmins_deliversCredentials() {
      // given
      given(countActiveAdminAccountsPort.countActive()).willReturn(0L);
      List<GeneratedAdminCredentials> credentials = buildCredentials(2);
      given(seedProvisioner.provision(2, UserRole.ADMIN_SEED)).willReturn(credentials);

      // when
      service.execute();

      // then
      verify(bootstrapDeliveryPort).deliver(eq(credentials));
    }
  }

  @Nested
  @DisplayName("실패 케이스")
  class FailureCases {

    @Test
    @DisplayName("[M-161] execute propagates exception from seedProvisioner")
    void execute_provisionerThrows_propagatesException() {
      // given
      given(countActiveAdminAccountsPort.countActive()).willReturn(0L);
      given(seedProvisioner.provision(2, UserRole.ADMIN_SEED))
          .willThrow(new AdminCredentialGenerationException("Failed after 5 attempts"));

      // when & then
      assertThatThrownBy(() -> service.execute())
          .isInstanceOf(AdminCredentialGenerationException.class)
          .hasMessageContaining("5 attempts");
      verify(bootstrapDeliveryPort, never()).deliver(any());
    }

    @Test
    @DisplayName("[M-162] execute propagates exception from delivery port")
    void execute_deliveryThrows_propagatesException() {
      // given
      given(countActiveAdminAccountsPort.countActive()).willReturn(0L);
      List<GeneratedAdminCredentials> credentials = buildCredentials(2);
      given(seedProvisioner.provision(2, UserRole.ADMIN_SEED)).willReturn(credentials);
      org.mockito.BDDMockito.willThrow(new RuntimeException("delivery failed"))
          .given(bootstrapDeliveryPort)
          .deliver(any());

      // when & then
      assertThatThrownBy(() -> service.execute())
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("delivery failed");
    }
  }
}
