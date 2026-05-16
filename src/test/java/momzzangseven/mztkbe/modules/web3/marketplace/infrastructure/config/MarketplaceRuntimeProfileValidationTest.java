package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import momzzangseven.mztkbe.global.error.web3.Web3ConfigInvalidException;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.PrecheckMarketplacePurchaseUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.PrepareMarketplaceUserExecutionUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.BuildMarketplaceEscrowCallDataPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.BuildMarketplaceUserExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceActiveWalletPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplacePurchaseConfigPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.PrecheckMarketplacePurchaseFundingPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.SignMarketplaceServerSigPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.SubmitMarketplaceExecutionDraftPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("Marketplace runtime profile validation test")
class MarketplaceRuntimeProfileValidationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              MarketplaceExecutionServiceConfig.class, MarketplaceRuntimeProfileValidator.class);

  @Test
  @DisplayName("fail-fast가 꺼져 있으면 marketplace user execution bean 부재를 허용한다")
  void doesNotValidateWhenFailFastDisabled() {
    contextRunner
        .withPropertyValues(
            "web3.eip7702.enabled=true", "web3.marketplace.user-execution.fail-fast=false")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(MarketplaceRuntimeProfileValidator.class);
              assertThat(context).doesNotHaveBean(PrecheckMarketplacePurchaseUseCase.class);
              assertThat(context).doesNotHaveBean(PrepareMarketplaceUserExecutionUseCase.class);
            });
  }

  @Test
  @DisplayName("fail-fast가 켜져 있으면 필수 marketplace user execution bean 부재 시 기동을 막는다")
  void failsFastWhenRequiredBeansAreMissing() {
    contextRunner
        .withPropertyValues(
            "web3.eip7702.enabled=true", "web3.marketplace.user-execution.fail-fast=true")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(rootCause(context.getStartupFailure()))
                  .isInstanceOf(Web3ConfigInvalidException.class)
                  .hasMessageContaining("Marketplace user execution runtime misconfigured")
                  .hasMessageContaining("PrecheckMarketplacePurchaseUseCase")
                  .hasMessageContaining("PrepareMarketplaceUserExecutionUseCase");
            });
  }

  @Test
  @DisplayName("fail-fast 운영 profile은 필수 marketplace user execution wiring이 모두 있을 때만 통과한다")
  void passesWhenRequiredBeansExist() {
    contextRunner
        .withBean(
            LoadMarketplaceActiveWalletPort.class,
            () -> mock(LoadMarketplaceActiveWalletPort.class))
        .withBean(
            LoadMarketplacePurchaseConfigPort.class,
            () -> mock(LoadMarketplacePurchaseConfigPort.class))
        .withBean(
            PrecheckMarketplacePurchaseFundingPort.class,
            () -> mock(PrecheckMarketplacePurchaseFundingPort.class))
        .withBean(
            BuildMarketplaceEscrowCallDataPort.class,
            () -> mock(BuildMarketplaceEscrowCallDataPort.class))
        .withBean(
            SignMarketplaceServerSigPort.class, () -> mock(SignMarketplaceServerSigPort.class))
        .withBean(
            BuildMarketplaceUserExecutionDraftPort.class,
            () -> mock(BuildMarketplaceUserExecutionDraftPort.class))
        .withBean(
            SubmitMarketplaceExecutionDraftPort.class,
            () -> mock(SubmitMarketplaceExecutionDraftPort.class))
        .withPropertyValues(
            "web3.eip7702.enabled=true", "web3.marketplace.user-execution.fail-fast=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(MarketplaceRuntimeProfileValidator.class);
              assertThat(context).hasSingleBean(PrecheckMarketplacePurchaseUseCase.class);
              assertThat(context).hasSingleBean(PrepareMarketplaceUserExecutionUseCase.class);
            });
  }

  private static Throwable rootCause(Throwable failure) {
    Throwable current = failure;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }
}
