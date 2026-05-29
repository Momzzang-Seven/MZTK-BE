package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionAuthorityView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceWeb3InternalExecutionPolicyStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadMarketplaceAdminExecutionAuthorityPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadMarketplaceWeb3InternalExecutionPolicyPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ValidateMarketplaceWeb3AutoSettleConfigurationServiceTest {

  @Mock private LoadMarketplaceWeb3InternalExecutionPolicyPort loadPolicyPort;
  @Mock private LoadMarketplaceAdminExecutionAuthorityPort loadAuthorityPort;

  @Test
  void returnsOkWhenPolicyAndAuthorityAreValid() {
    given(loadPolicyPort.loadInternalExecutionPolicy())
        .willReturn(new MarketplaceWeb3InternalExecutionPolicyStatus(true, true, true));
    given(loadAuthorityPort.load()).willReturn(registeredAuthority());

    var result =
        new ValidateMarketplaceWeb3AutoSettleConfigurationService(loadPolicyPort, loadAuthorityPort)
            .validate();

    assertThat(result.hasAuthorityWarning()).isFalse();
    assertThat(result.authorityMisconfigurationMessage()).isNull();
  }

  @Test
  void rejectsDisabledInternalExecutionPolicy() {
    given(loadPolicyPort.loadInternalExecutionPolicy())
        .willReturn(new MarketplaceWeb3InternalExecutionPolicyStatus(false, true, true));

    assertThatThrownBy(
            () ->
                new ValidateMarketplaceWeb3AutoSettleConfigurationService(
                        loadPolicyPort, loadAuthorityPort)
                    .validate())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("web3.execution.internal.enabled=true");
  }

  @Test
  void rejectsMissingMarketplaceAdminSettleAction() {
    given(loadPolicyPort.loadInternalExecutionPolicy())
        .willReturn(new MarketplaceWeb3InternalExecutionPolicyStatus(true, false, true));

    assertThatThrownBy(
            () ->
                new ValidateMarketplaceWeb3AutoSettleConfigurationService(
                        loadPolicyPort, loadAuthorityPort)
                    .validate())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MARKETPLACE_ADMIN_SETTLE");
  }

  @Test
  void allowsMissingMarketplaceAdminRefundAction() {
    given(loadPolicyPort.loadInternalExecutionPolicy())
        .willReturn(new MarketplaceWeb3InternalExecutionPolicyStatus(true, true, false));
    given(loadAuthorityPort.load()).willReturn(registeredAuthority());

    var result =
        new ValidateMarketplaceWeb3AutoSettleConfigurationService(loadPolicyPort, loadAuthorityPort)
            .validate();

    assertThat(result.hasAuthorityWarning()).isFalse();
  }

  @Test
  void returnsAuthorityWarningWhenSignerIsUnavailable() {
    given(loadPolicyPort.loadInternalExecutionPolicy())
        .willReturn(new MarketplaceWeb3InternalExecutionPolicyStatus(true, true, true));
    given(loadAuthorityPort.load())
        .willReturn(MarketplaceAdminExecutionAuthorityView.serverRelayerOnly());

    var result =
        new ValidateMarketplaceWeb3AutoSettleConfigurationService(loadPolicyPort, loadAuthorityPort)
            .validate();

    assertThat(result.hasAuthorityWarning()).isTrue();
    assertThat(result.authorityMisconfigurationMessage()).contains("signer is unavailable");
  }

  @Test
  void returnsAuthorityWarningWhenRelayerCheckFails() {
    given(loadPolicyPort.loadInternalExecutionPolicy())
        .willReturn(new MarketplaceWeb3InternalExecutionPolicyStatus(true, true, true));
    given(loadAuthorityPort.load())
        .willReturn(
            new MarketplaceAdminExecutionAuthorityView(
                false,
                "SERVER_RELAYER_ONLY",
                true,
                "0x" + "1".repeat(40),
                false,
                MarketplaceAdminExecutionAuthorityView.RELAYER_REGISTRATION_CHECK_FAILED,
                true,
                false));

    var result =
        new ValidateMarketplaceWeb3AutoSettleConfigurationService(loadPolicyPort, loadAuthorityPort)
            .validate();

    assertThat(result.hasAuthorityWarning()).isTrue();
    assertThat(result.authorityMisconfigurationMessage())
        .contains("failed to validate current server signer relayer registration");
  }

  @Test
  void returnsAuthorityWarningWhenRelayerIsNotRegistered() {
    given(loadPolicyPort.loadInternalExecutionPolicy())
        .willReturn(new MarketplaceWeb3InternalExecutionPolicyStatus(true, true, true));
    given(loadAuthorityPort.load())
        .willReturn(
            new MarketplaceAdminExecutionAuthorityView(
                false, "SERVER_RELAYER_ONLY", true, "0x" + "1".repeat(40), false, false, false));

    var result =
        new ValidateMarketplaceWeb3AutoSettleConfigurationService(loadPolicyPort, loadAuthorityPort)
            .validate();

    assertThatCode(result::hasAuthorityWarning).doesNotThrowAnyException();
    assertThat(result.hasAuthorityWarning()).isTrue();
    assertThat(result.authorityMisconfigurationMessage())
        .contains("not registered as relayer")
        .contains("0x" + "1".repeat(40));
  }

  private MarketplaceAdminExecutionAuthorityView registeredAuthority() {
    return new MarketplaceAdminExecutionAuthorityView(
        false, "SERVER_RELAYER_ONLY", true, "0x" + "1".repeat(40), true, false, false);
  }
}
