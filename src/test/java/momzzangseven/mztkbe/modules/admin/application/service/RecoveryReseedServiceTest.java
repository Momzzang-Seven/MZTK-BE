package momzzangseven.mztkbe.modules.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import momzzangseven.mztkbe.global.audit.application.port.out.RecordAdminAuditPort;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.error.admin.AdminCredentialGenerationException;
import momzzangseven.mztkbe.global.error.admin.RecoveryAnchorUnavailableException;
import momzzangseven.mztkbe.global.error.admin.RecoveryRejectedException;
import momzzangseven.mztkbe.modules.admin.application.dto.RecoveryReseedCommand;
import momzzangseven.mztkbe.modules.admin.application.dto.RecoveryReseedResult;
import momzzangseven.mztkbe.modules.admin.application.port.out.BootstrapDeliveryPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.LoadSeedPolicyPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.RecoveryAnchorPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.SoftDeleteAdminAccountsPort;
import momzzangseven.mztkbe.modules.admin.domain.vo.AdminRole;
import momzzangseven.mztkbe.modules.admin.domain.vo.GeneratedAdminCredentials;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecoveryReseedService 단위 테스트")
class RecoveryReseedServiceTest {

  @Mock private LoadSeedPolicyPort loadSeedPolicyPort;
  @Mock private RecoveryAnchorPort recoveryAnchorPort;
  @Mock private SoftDeleteAdminAccountsPort softDeleteAdminAccountsPort;
  @Mock private SeedProvisioner seedProvisioner;
  @Mock private BootstrapDeliveryPort bootstrapDeliveryPort;
  @Mock private RecordAdminAuditPort recordAdminAuditPort;

  @InjectMocks private RecoveryReseedService service;

  private static final String CORRECT_ANCHOR = "correct-anchor";
  private static final int SEED_COUNT = 2;
  private static final String DELIVERY_TARGET = "mztk/admin/bootstrap-delivery";

  private List<GeneratedAdminCredentials> buildCredentials(int count) {
    return java.util.stream.IntStream.rangeClosed(1, count)
        .mapToObj(i -> new GeneratedAdminCredentials("1000000" + i, "Abcdefghij123456789!"))
        .toList();
  }

  private void givenSeedPolicy() {
    given(loadSeedPolicyPort.getSeedCount()).willReturn(SEED_COUNT);
    given(loadSeedPolicyPort.getDeliveryTarget()).willReturn(DELIVERY_TARGET);
  }

  private void givenAnchorMatches() {
    given(recoveryAnchorPort.loadAnchor()).willReturn(CORRECT_ANCHOR);
    given(softDeleteAdminAccountsPort.softDeleteAll()).willReturn(3);
  }

  private List<GeneratedAdminCredentials> givenFullHappyPath() {
    givenSeedPolicy();
    givenAnchorMatches();
    List<GeneratedAdminCredentials> credentials = buildCredentials(2);
    given(seedProvisioner.provision(SEED_COUNT, AdminRole.ADMIN_SEED)).willReturn(credentials);
    return credentials;
  }

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName(
        "[M-171] execute succeeds when anchor matches"
            + " — soft-deletes all, provisions 2 seeds, delivers, audits success")
    void execute_anchorMatches_fullRecoveryFlow() {
      // given
      List<GeneratedAdminCredentials> credentials = givenFullHappyPath();

      // when
      RecoveryReseedResult result =
          service.execute(new RecoveryReseedCommand(CORRECT_ANCHOR, "192.168.1.1"));

      // then
      assertThat(result.newSeedCount()).isEqualTo(2);
      assertThat(result.deliveredVia()).isEqualTo(DELIVERY_TARGET);
      verify(softDeleteAdminAccountsPort).softDeleteAll();
      verify(seedProvisioner).provision(2, AdminRole.ADMIN_SEED);
      verify(bootstrapDeliveryPort).deliver(credentials);

      ArgumentCaptor<RecordAdminAuditPort.AuditCommand> captor =
          ArgumentCaptor.forClass(RecordAdminAuditPort.AuditCommand.class);
      verify(recordAdminAuditPort).record(captor.capture());
      RecordAdminAuditPort.AuditCommand audit = captor.getValue();
      assertThat(audit.actionType()).isEqualTo("RECOVERY_SUCCESS");
      assertThat(audit.success()).isTrue();
      assertThat(audit.detail()).containsEntry("sourceIp", "192.168.1.1");
    }

    @Test
    @DisplayName("[M-174] execute records audit with null operatorId for RECOVERY_SUCCESS")
    void execute_anchorMatches_auditHasNullOperatorId() {
      // given
      givenFullHappyPath();

      // when
      service.execute(new RecoveryReseedCommand(CORRECT_ANCHOR, "192.168.1.1"));

      // then
      ArgumentCaptor<RecordAdminAuditPort.AuditCommand> captor =
          ArgumentCaptor.forClass(RecordAdminAuditPort.AuditCommand.class);
      verify(recordAdminAuditPort).record(captor.capture());
      RecordAdminAuditPort.AuditCommand audit = captor.getValue();
      assertThat(audit.operatorId()).isNull();
      assertThat(audit.targetType()).isEqualTo(AuditTargetType.ADMIN_ACCOUNT);
    }

    @Test
    @DisplayName("[M-176] execute uses \"unknown\" for sourceIp in audit when sourceIp is null")
    void execute_nullSourceIp_auditsUnknown() {
      // given
      givenFullHappyPath();

      // when
      service.execute(new RecoveryReseedCommand(CORRECT_ANCHOR, null));

      // then
      ArgumentCaptor<RecordAdminAuditPort.AuditCommand> captor =
          ArgumentCaptor.forClass(RecordAdminAuditPort.AuditCommand.class);
      verify(recordAdminAuditPort).record(captor.capture());
      assertThat(captor.getValue().detail()).containsEntry("sourceIp", "unknown");
    }

    @Test
    @DisplayName("[M-177] execute still succeeds when audit recording fails (audit is best-effort)")
    void execute_auditFails_operationStillSucceeds() {
      // given
      givenFullHappyPath();
      willThrow(new RuntimeException("audit DB down")).given(recordAdminAuditPort).record(any());

      // when
      RecoveryReseedResult result =
          service.execute(new RecoveryReseedCommand(CORRECT_ANCHOR, "192.168.1.1"));

      // then
      assertThat(result.newSeedCount()).isEqualTo(2);
      assertThat(result.deliveredVia()).isEqualTo(DELIVERY_TARGET);
    }
  }

  @Nested
  @DisplayName("실패 케이스")
  class FailureCases {

    @Test
    @DisplayName("[M-172] execute throws RecoveryRejectedException when anchor does not match")
    void execute_anchorMismatch_throwsRejected() {
      // given
      given(recoveryAnchorPort.loadAnchor()).willReturn("stored-anchor");

      // when & then
      assertThatThrownBy(
              () -> service.execute(new RecoveryReseedCommand("wrong-anchor", "10.0.0.1")))
          .isInstanceOf(RecoveryRejectedException.class);

      ArgumentCaptor<RecordAdminAuditPort.AuditCommand> captor =
          ArgumentCaptor.forClass(RecordAdminAuditPort.AuditCommand.class);
      verify(recordAdminAuditPort).record(captor.capture());
      RecordAdminAuditPort.AuditCommand audit = captor.getValue();
      assertThat(audit.actionType()).isEqualTo("RECOVERY_REJECTED");
      assertThat(audit.success()).isFalse();
      assertThat(audit.detail()).containsEntry("sourceIp", "10.0.0.1");

      verify(softDeleteAdminAccountsPort, never()).softDeleteAll();
      verify(seedProvisioner, never()).provision(anyInt(), any());
      verify(bootstrapDeliveryPort, never()).deliver(any());
    }

    @Test
    @DisplayName(
        "[M-173] execute throws RecoveryAnchorUnavailableException when anchor port throws")
    void execute_anchorPortThrows_throwsUnavailable() {
      // given
      given(recoveryAnchorPort.loadAnchor()).willThrow(new RuntimeException("SM unavailable"));

      // when & then
      assertThatThrownBy(() -> service.execute(new RecoveryReseedCommand("any-anchor", "10.0.0.1")))
          .isInstanceOf(RecoveryAnchorUnavailableException.class)
          .hasMessageContaining("Cannot load recovery anchor")
          .hasCauseInstanceOf(RuntimeException.class);

      verify(softDeleteAdminAccountsPort, never()).softDeleteAll();
      verify(seedProvisioner, never()).provision(anyInt(), any());
      verify(bootstrapDeliveryPort, never()).deliver(any());
      verify(recordAdminAuditPort, never()).record(any());
    }

    @Test
    @DisplayName("[M-175] execute records audit with null operatorId for RECOVERY_REJECTED")
    void execute_anchorMismatch_auditHasNullOperatorId() {
      // given
      given(recoveryAnchorPort.loadAnchor()).willReturn("stored-anchor");

      // when & then
      assertThatThrownBy(
              () -> service.execute(new RecoveryReseedCommand("wrong-anchor", "10.0.0.1")))
          .isInstanceOf(RecoveryRejectedException.class);

      ArgumentCaptor<RecordAdminAuditPort.AuditCommand> captor =
          ArgumentCaptor.forClass(RecordAdminAuditPort.AuditCommand.class);
      verify(recordAdminAuditPort).record(captor.capture());
      RecordAdminAuditPort.AuditCommand audit = captor.getValue();
      assertThat(audit.operatorId()).isNull();
      assertThat(audit.actionType()).isEqualTo("RECOVERY_REJECTED");
    }

    @Test
    @DisplayName(
        "[M-178] execute still throws RecoveryRejectedException"
            + " when audit recording fails on rejection")
    void execute_anchorMismatchAndAuditFails_throwsRejected() {
      // given
      given(recoveryAnchorPort.loadAnchor()).willReturn("stored-anchor");
      willThrow(new RuntimeException("audit DB down")).given(recordAdminAuditPort).record(any());

      // when & then
      assertThatThrownBy(
              () -> service.execute(new RecoveryReseedCommand("wrong-anchor", "10.0.0.1")))
          .isInstanceOf(RecoveryRejectedException.class);

      verify(softDeleteAdminAccountsPort, never()).softDeleteAll();
    }

    @Test
    @DisplayName("[M-179] execute propagates exception from softDeleteAdminAccountsPort")
    void execute_softDeleteThrows_propagates() {
      // given
      given(recoveryAnchorPort.loadAnchor()).willReturn(CORRECT_ANCHOR);
      given(softDeleteAdminAccountsPort.softDeleteAll())
          .willThrow(new RuntimeException("DB error"));

      // when & then
      assertThatThrownBy(
              () -> service.execute(new RecoveryReseedCommand(CORRECT_ANCHOR, "192.168.1.1")))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("DB error");

      verify(seedProvisioner, never()).provision(anyInt(), any());
    }

    @Test
    @DisplayName("[M-180] execute propagates exception from seedProvisioner")
    void execute_provisionerThrows_propagates() {
      // given
      given(loadSeedPolicyPort.getSeedCount()).willReturn(SEED_COUNT);
      givenAnchorMatches();
      given(seedProvisioner.provision(SEED_COUNT, AdminRole.ADMIN_SEED))
          .willThrow(new AdminCredentialGenerationException("Failed after 5 attempts"));

      // when & then
      assertThatThrownBy(
              () -> service.execute(new RecoveryReseedCommand(CORRECT_ANCHOR, "192.168.1.1")))
          .isInstanceOf(AdminCredentialGenerationException.class);

      verify(bootstrapDeliveryPort, never()).deliver(any());
    }

    @Test
    @DisplayName("[M-181] execute propagates exception from bootstrapDeliveryPort")
    void execute_deliveryThrows_propagates() {
      // given
      given(loadSeedPolicyPort.getSeedCount()).willReturn(SEED_COUNT);
      givenAnchorMatches();
      List<GeneratedAdminCredentials> credentials = buildCredentials(2);
      given(seedProvisioner.provision(SEED_COUNT, AdminRole.ADMIN_SEED)).willReturn(credentials);
      willThrow(new RuntimeException("delivery failed"))
          .given(bootstrapDeliveryPort)
          .deliver(any());

      // when & then
      assertThatThrownBy(
              () -> service.execute(new RecoveryReseedCommand(CORRECT_ANCHOR, "192.168.1.1")))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("delivery failed");
    }
  }
}
