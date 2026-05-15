package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.wallet.WalletApprovalUnavailableException;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.BuildWalletApprovalExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.CancelWalletApprovalExecutionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalCapabilityPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalExecutionStatePort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalExecutionSupportPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SubmitWalletApprovalExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SyncWalletApprovalExecutionSuccessPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("WalletApprovalExecutionConfig unit test")
class WalletApprovalExecutionConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(WalletApprovalExecutionConfig.class);

  @Test
  void fallbackPortsLoadAndFailFastWhenEip7702ApprovalBeansAreMissing() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(BuildWalletApprovalExecutionDraftPort.class);
          assertThat(context).hasSingleBean(SubmitWalletApprovalExecutionDraftPort.class);
          assertThat(context).hasSingleBean(LoadWalletApprovalCapabilityPort.class);
          assertThat(context).hasSingleBean(LoadWalletApprovalExecutionSupportPort.class);
          assertThat(context).hasSingleBean(LoadWalletApprovalExecutionStatePort.class);
          assertThat(context).hasSingleBean(CancelWalletApprovalExecutionPort.class);
          assertThat(context).hasSingleBean(SyncWalletApprovalExecutionSuccessPort.class);

          assertThat(context.getBean(LoadWalletApprovalCapabilityPort.class).load().available())
              .isFalse();
          assertThat(
                  context
                      .getBean(LoadWalletApprovalExecutionStatePort.class)
                      .loadByExecutionIntentId(1L, "intent-1"))
              .isEmpty();
          assertThatThrownBy(
                  () -> context.getBean(SubmitWalletApprovalExecutionDraftPort.class).submit(null))
              .isInstanceOf(WalletApprovalUnavailableException.class)
              .hasMessageContaining("wallet approval execution is unavailable");
          assertThatThrownBy(
                  () ->
                      context
                          .getBean(LoadWalletApprovalExecutionSupportPort.class)
                          .load("0x0000000000000000000000000000000000000001"))
              .isInstanceOf(WalletApprovalUnavailableException.class)
              .hasMessageContaining("wallet approval execution is unavailable");
          assertThatThrownBy(
                  () ->
                      context
                          .getBean(CancelWalletApprovalExecutionPort.class)
                          .cancelIfSignable("intent-1", "CODE", "reason"))
              .isInstanceOf(WalletApprovalUnavailableException.class)
              .hasMessageContaining("wallet approval execution is unavailable");
        });
  }
}
