package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.treasury;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminServerSignerFailureReason;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminServerSignerSlotStatus;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminServerSignerView;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ExecutionSignerCapabilityView;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ExecutionSignerFailureReason;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ExecutionSignerSlotStatus;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.ProbeTreasuryWalletCapabilityUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProbeSponsorSignerCapabilityAdapterTest {

  private static final String SPONSOR_ALIAS = "sponsor-treasury";
  private static final String SIGNER_ADDRESS = "0x" + "a".repeat(40);

  @Mock private ProbeTreasuryWalletCapabilityUseCase probeTreasuryWalletCapabilityUseCase;
  @InjectMocks private ProbeSponsorSignerCapabilityAdapter adapter;

  @Test
  void probe_delegatesToTreasuryUseCase_withSponsorAlias_andMapsReadyView() {
    ExecutionSignerCapabilityView source =
        ExecutionSignerCapabilityView.ready(SPONSOR_ALIAS, SIGNER_ADDRESS);
    when(probeTreasuryWalletCapabilityUseCase.probe(SPONSOR_ALIAS)).thenReturn(source);

    QnaAdminServerSignerView actual = adapter.probe();

    assertThat(actual.walletAlias()).isEqualTo(SPONSOR_ALIAS);
    assertThat(actual.slotStatus()).isEqualTo(QnaAdminServerSignerSlotStatus.READY);
    assertThat(actual.failureReason()).isEqualTo(QnaAdminServerSignerFailureReason.NONE);
    assertThat(actual.signerAddress()).isEqualTo(SIGNER_ADDRESS);
    assertThat(actual.signable()).isTrue();
    verify(probeTreasuryWalletCapabilityUseCase).probe(SPONSOR_ALIAS);
  }

  @Test
  void probe_propagatesUnavailableSlotStatus_withProvisionedFailure() {
    ExecutionSignerCapabilityView source =
        ExecutionSignerCapabilityView.provisionedUnavailable(
            SPONSOR_ALIAS, ExecutionSignerFailureReason.KMS_KEY_DISABLED);
    when(probeTreasuryWalletCapabilityUseCase.probe(SPONSOR_ALIAS)).thenReturn(source);

    QnaAdminServerSignerView actual = adapter.probe();

    assertThat(actual.slotStatus()).isEqualTo(QnaAdminServerSignerSlotStatus.PROVISIONED);
    assertThat(actual.failureReason())
        .isEqualTo(QnaAdminServerSignerFailureReason.KMS_KEY_DISABLED);
    assertThat(actual.signerAddress()).isNull();
    assertThat(actual.signable()).isFalse();
  }

  @ParameterizedTest
  @EnumSource(ExecutionSignerSlotStatus.class)
  void probe_mapsEverySlotStatus_byName(ExecutionSignerSlotStatus slot) {
    ExecutionSignerCapabilityView source = sourceForSlot(slot);
    when(probeTreasuryWalletCapabilityUseCase.probe(SPONSOR_ALIAS)).thenReturn(source);

    QnaAdminServerSignerView actual = adapter.probe();

    assertThat(actual.slotStatus().name()).isEqualTo(slot.name());
  }

  @ParameterizedTest
  @EnumSource(ExecutionSignerFailureReason.class)
  void probe_mapsEveryFailureReason_byName(ExecutionSignerFailureReason failure) {
    ExecutionSignerCapabilityView source = sourceForFailure(failure);
    when(probeTreasuryWalletCapabilityUseCase.probe(SPONSOR_ALIAS)).thenReturn(source);

    QnaAdminServerSignerView actual = adapter.probe();

    assertThat(actual.failureReason().name()).isEqualTo(failure.name());
  }

  private static ExecutionSignerCapabilityView sourceForSlot(ExecutionSignerSlotStatus slot) {
    return switch (slot) {
      case SLOT_MISSING -> ExecutionSignerCapabilityView.slotMissing(SPONSOR_ALIAS);
      case UNPROVISIONED -> ExecutionSignerCapabilityView.unprovisioned(SPONSOR_ALIAS);
      case PROVISIONED ->
          ExecutionSignerCapabilityView.provisionedUnavailable(
              SPONSOR_ALIAS, ExecutionSignerFailureReason.KMS_KEY_DISABLED);
      case READY -> ExecutionSignerCapabilityView.ready(SPONSOR_ALIAS, SIGNER_ADDRESS);
    };
  }

  private static ExecutionSignerCapabilityView sourceForFailure(
      ExecutionSignerFailureReason failure) {
    if (failure == ExecutionSignerFailureReason.NONE) {
      return ExecutionSignerCapabilityView.unprovisioned(SPONSOR_ALIAS);
    }
    return ExecutionSignerCapabilityView.provisionedUnavailable(SPONSOR_ALIAS, failure);
  }
}
