package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceAdminExecutionAuthorityStatus;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceInternalExecutionPolicyStatus;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.LoadMarketplaceAdminExecutionAuthorityUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceInternalExecutionPolicyPort;
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

  @Mock
  private LoadMarketplaceInternalExecutionPolicyPort loadMarketplaceInternalExecutionPolicyPort;

  @Mock
  private LoadMarketplaceAdminExecutionAuthorityUseCase
      loadMarketplaceAdminExecutionAuthorityUseCase;

  private MarketplaceAdminExecutionConfigurationValidator validator;

  @BeforeEach
  void setUp() {
    validator =
        new MarketplaceAdminExecutionConfigurationValidator(
            loadMarketplaceInternalExecutionPolicyPort,
            loadMarketplaceAdminExecutionAuthorityUseCase);
  }

  @Test
  @DisplayName("internal issuer marketplace admin actions + relayer 등록 signer 이면 통과한다")
  void validateConfiguration_allowsRegisteredMarketplaceSigner() {
    when(loadMarketplaceInternalExecutionPolicyPort.load()).thenReturn(enabledPolicy());
    when(loadMarketplaceAdminExecutionAuthorityUseCase.execute()).thenReturn(registeredAuthority());

    assertThatCode(validator::validateConfiguration).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("marketplace admin action-policy 누락이면 startup validation 이 실패한다")
  void validateConfiguration_rejectsMissingMarketplaceAdminPolicy() {
    when(loadMarketplaceInternalExecutionPolicyPort.load())
        .thenReturn(new MarketplaceInternalExecutionPolicyStatus(true, true, false));

    assertThatThrownBy(validator::validateConfiguration)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MARKETPLACE_ADMIN_REFUND");
  }

  @Test
  @DisplayName("fail-fast=false 이면 signer unavailable 은 startup 을 막지 않는다")
  void validateConfiguration_allowsUnavailableSignerWhenFailFastOff() {
    when(loadMarketplaceInternalExecutionPolicyPort.load()).thenReturn(enabledPolicy());
    when(loadMarketplaceAdminExecutionAuthorityUseCase.execute())
        .thenReturn(MarketplaceAdminExecutionAuthorityStatus.serverRelayerOnly());

    assertThatCode(validator::validateConfiguration).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("fail-fast=true 이면 relayer 미등록 signer 를 startup 에서 차단한다")
  void validateConfiguration_rejectsUnregisteredRelayerWhenFailFastOn() {
    ReflectionTestUtils.setField(validator, "failFast", true);
    when(loadMarketplaceInternalExecutionPolicyPort.load()).thenReturn(enabledPolicy());
    when(loadMarketplaceAdminExecutionAuthorityUseCase.execute())
        .thenReturn(
            new MarketplaceAdminExecutionAuthorityStatus(
                false,
                MarketplaceAdminExecutionAuthorityStatus.SERVER_RELAYER_ONLY,
                true,
                "0x2222222222222222222222222222222222222222",
                false,
                MarketplaceAdminExecutionAuthorityStatus.RELAYER_REGISTRATION_NOT_REGISTERED));

    assertThatThrownBy(validator::validateConfiguration)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not registered as relayer");
  }

  private MarketplaceInternalExecutionPolicyStatus enabledPolicy() {
    return new MarketplaceInternalExecutionPolicyStatus(true, true, true);
  }

  private MarketplaceAdminExecutionAuthorityStatus registeredAuthority() {
    return new MarketplaceAdminExecutionAuthorityStatus(
        false,
        MarketplaceAdminExecutionAuthorityStatus.SERVER_RELAYER_ONLY,
        true,
        "0x2222222222222222222222222222222222222222",
        true,
        MarketplaceAdminExecutionAuthorityStatus.RELAYER_REGISTRATION_REGISTERED);
  }
}
