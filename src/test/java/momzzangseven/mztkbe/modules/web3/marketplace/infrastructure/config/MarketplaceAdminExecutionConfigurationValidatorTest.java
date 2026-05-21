package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.web3.execution.application.dto.InternalExecutionIssuerPolicyView;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetInternalExecutionIssuerPolicyUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.web3.MarketplaceContractCallSupport;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ExecutionSignerCapabilityView;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ExecutionSignerFailureReason;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ExecutionSignerSlotStatus;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.ProbeTreasuryWalletCapabilityUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("MarketplaceAdminExecutionConfigurationValidator")
class MarketplaceAdminExecutionConfigurationValidatorTest {

  @Mock private GetInternalExecutionIssuerPolicyUseCase getInternalExecutionIssuerPolicyUseCase;
  @Mock private ProbeTreasuryWalletCapabilityUseCase probeTreasuryWalletCapabilityUseCase;
  @Mock private MarketplaceContractCallSupport marketplaceContractCallSupport;

  private MarketplaceEscrowProperties marketplaceEscrowProperties;
  private MarketplaceAdminExecutionConfigurationValidator validator;

  @BeforeEach
  void setUp() {
    marketplaceEscrowProperties = new MarketplaceEscrowProperties();
    marketplaceEscrowProperties.setMarketplaceContractAddress(
        "0x1111111111111111111111111111111111111111");
    validator =
        new MarketplaceAdminExecutionConfigurationValidator(
            getInternalExecutionIssuerPolicyUseCase,
            probeTreasuryWalletCapabilityUseCase,
            marketplaceContractCallSupport,
            marketplaceEscrowProperties);
  }

  @Test
  @DisplayName("internal issuer marketplace admin actions + relayer 등록 signer 이면 통과한다")
  void validateConfiguration_allowsRegisteredMarketplaceSigner() {
    when(getInternalExecutionIssuerPolicyUseCase.getPolicy()).thenReturn(enabledPolicy());
    when(probeTreasuryWalletCapabilityUseCase.probe(TreasuryRole.MARKETPLACE_SIGNER.toAlias()))
        .thenReturn(
            ExecutionSignerCapabilityView.ready(
                TreasuryRole.MARKETPLACE_SIGNER.toAlias(),
                "0x2222222222222222222222222222222222222222"));
    when(marketplaceContractCallSupport.isRelayerRegistered(
            "0x1111111111111111111111111111111111111111",
            "0x2222222222222222222222222222222222222222"))
        .thenReturn(true);

    assertThatCode(validator::validateConfiguration).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("marketplace admin action-policy 누락이면 startup validation 이 실패한다")
  void validateConfiguration_rejectsMissingMarketplaceAdminPolicy() {
    when(getInternalExecutionIssuerPolicyUseCase.getPolicy())
        .thenReturn(new InternalExecutionIssuerPolicyView(true, true, true, true, false));

    assertThatThrownBy(validator::validateConfiguration)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MARKETPLACE_ADMIN_REFUND");
  }

  @Test
  @DisplayName("fail-fast=false 이면 signer unavailable 은 startup 을 막지 않는다")
  void validateConfiguration_allowsUnavailableSignerWhenFailFastOff() {
    when(getInternalExecutionIssuerPolicyUseCase.getPolicy()).thenReturn(enabledPolicy());
    when(probeTreasuryWalletCapabilityUseCase.probe(TreasuryRole.MARKETPLACE_SIGNER.toAlias()))
        .thenReturn(
            ExecutionSignerCapabilityView.unavailable(
                TreasuryRole.MARKETPLACE_SIGNER.toAlias(),
                ExecutionSignerSlotStatus.PROVISIONED,
                ExecutionSignerFailureReason.KMS_KEY_DISABLED));

    assertThatCode(validator::validateConfiguration).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("fail-fast=true 이면 relayer 미등록 signer 를 startup 에서 차단한다")
  void validateConfiguration_rejectsUnregisteredRelayerWhenFailFastOn() {
    ReflectionTestUtils.setField(validator, "failFast", true);
    when(getInternalExecutionIssuerPolicyUseCase.getPolicy()).thenReturn(enabledPolicy());
    when(probeTreasuryWalletCapabilityUseCase.probe(TreasuryRole.MARKETPLACE_SIGNER.toAlias()))
        .thenReturn(
            ExecutionSignerCapabilityView.ready(
                TreasuryRole.MARKETPLACE_SIGNER.toAlias(),
                "0x2222222222222222222222222222222222222222"));
    when(marketplaceContractCallSupport.isRelayerRegistered(
            "0x1111111111111111111111111111111111111111",
            "0x2222222222222222222222222222222222222222"))
        .thenReturn(false);

    assertThatThrownBy(validator::validateConfiguration)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not registered as relayer");
  }

  private InternalExecutionIssuerPolicyView enabledPolicy() {
    return new InternalExecutionIssuerPolicyView(true, true, true, true, true);
  }
}
