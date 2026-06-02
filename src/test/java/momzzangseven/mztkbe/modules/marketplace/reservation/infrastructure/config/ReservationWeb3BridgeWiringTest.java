package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.CancelReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowOrderPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionCandidatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionResumePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionWritePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationWalletPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.PrecheckReservationPurchasePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.PrepareReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3.ReservationEscrowOrderAdapter;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3.ReservationExecutionCancelAdapter;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3.ReservationExecutionCandidateAdapter;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3.ReservationExecutionResumeAdapter;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3.ReservationExecutionWriteAdapter;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3.ReservationMarketplaceExecutionAdapter;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3.ReservationPurchasePrecheckAdapter;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3.ReservationWalletAdapter;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CancelExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentCandidateResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentCandidatesQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentStateQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentStateResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummaryQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummaryResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ReplayConfirmedExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ReplayTerminatedExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.CancelExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionIntentCandidatesUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionIntentStateUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetLatestExecutionIntentSummaryUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ReplayConfirmedExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ReplayTerminatedExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.BuildMarketplaceUserExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceEscrowOrderPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplacePurchaseConfigPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.PrecheckMarketplacePurchaseFundingPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.SubmitMarketplaceExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.config.MarketplaceExecutionServiceConfig;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.GetActiveWalletAddressUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@DisplayName("Reservation Web3 bridge wiring")
class ReservationWeb3BridgeWiringTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(ObjectMapper.class, ObjectMapper::new)
          .withBean(
              LoadMarketplacePurchaseConfigPort.class,
              () -> mock(LoadMarketplacePurchaseConfigPort.class))
          .withBean(
              PrecheckMarketplacePurchaseFundingPort.class,
              () -> mock(PrecheckMarketplacePurchaseFundingPort.class))
          .withBean(
              BuildMarketplaceUserExecutionDraftPort.class,
              () -> mock(BuildMarketplaceUserExecutionDraftPort.class))
          .withBean(
              SubmitMarketplaceExecutionDraftPort.class,
              () -> mock(SubmitMarketplaceExecutionDraftPort.class))
          .withBean(
              LoadMarketplaceEscrowOrderPort.class,
              () -> mock(LoadMarketplaceEscrowOrderPort.class))
          .withBean(
              GetActiveWalletAddressUseCase.class, () -> mock(GetActiveWalletAddressUseCase.class))
          .withUserConfiguration(BridgeConfig.class);

  @Test
  @DisplayName("EIP-7702 enabled mode 는 real reservation bridge adapter 를 등록한다")
  void enabledModeRegistersRealReservationBridgeAdapters() {
    contextRunner
        .withPropertyValues("web3.eip7702.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(PrecheckReservationPurchasePort.class);
              assertThat(context.getBean(PrecheckReservationPurchasePort.class))
                  .isInstanceOf(ReservationPurchasePrecheckAdapter.class);
              assertThat(context.getBean(PrepareReservationEscrowExecutionPort.class))
                  .isInstanceOf(ReservationMarketplaceExecutionAdapter.class);
              assertThat(context.getBean(LoadReservationExecutionWritePort.class))
                  .isInstanceOf(ReservationExecutionWriteAdapter.class);
              assertThat(context.getBean(LoadReservationExecutionResumePort.class))
                  .isInstanceOf(ReservationExecutionResumeAdapter.class);
              assertThat(context.getBean(LoadReservationExecutionCandidatePort.class))
                  .isInstanceOf(ReservationExecutionCandidateAdapter.class);
              assertThat(context.getBean(LoadReservationWalletPort.class))
                  .isInstanceOf(ReservationWalletAdapter.class);
              assertThat(context.getBean(CancelReservationEscrowExecutionPort.class))
                  .isInstanceOf(ReservationExecutionCancelAdapter.class);
              assertThat(context.getBean(LoadReservationEscrowOrderPort.class))
                  .isInstanceOf(ReservationEscrowOrderAdapter.class);
              assertThat(context).doesNotHaveBean("disabledPrecheckReservationPurchasePort");
              assertThat(context).doesNotHaveBean("disabledPrepareReservationEscrowExecutionPort");
              assertThat(context).doesNotHaveBean("disabledLoadReservationWalletPort");
              assertThat(context).doesNotHaveBean("disabledLoadReservationEscrowPaymentConfigPort");
              assertThat(context).doesNotHaveBean("disabledLoadReservationEscrowOrderPort");
              assertThat(context).doesNotHaveBean("disabledLoadReservationExecutionWritePort");
              assertThat(context).doesNotHaveBean("disabledLoadReservationExecutionStatePort");
              assertThat(context).doesNotHaveBean("disabledLoadReservationExecutionResumePort");
              assertThat(context).doesNotHaveBean("disabledLoadReservationExecutionCandidatePort");
              assertThat(context).doesNotHaveBean("disabledCancelReservationEscrowExecutionPort");
              assertThat(context)
                  .doesNotHaveBean("disabledReplayConfirmedReservationExecutionPort");
              assertThat(context)
                  .doesNotHaveBean("disabledReplayTerminatedReservationExecutionPort");
            });
  }

  @Test
  @DisplayName("EIP-7702 disabled mode 는 disabled fallback 만 등록한다")
  void disabledModeRegistersDisabledFallbacks() {
    contextRunner
        .withPropertyValues("web3.eip7702.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(ReservationPurchasePrecheckAdapter.class);
              assertThat(context).doesNotHaveBean(ReservationMarketplaceExecutionAdapter.class);
              assertThat(context).doesNotHaveBean(ReservationEscrowOrderAdapter.class);
              assertThat(context).hasBean("disabledPrecheckReservationPurchasePort");
              assertThat(context).hasBean("disabledPrepareReservationEscrowExecutionPort");
              assertThat(context).hasBean("disabledLoadReservationEscrowOrderPort");
            });
  }

  @Configuration
  @Import({
    ReservationPurchasePrecheckAdapter.class,
    ReservationMarketplaceExecutionAdapter.class,
    ReservationExecutionWriteAdapter.class,
    ReservationExecutionResumeAdapter.class,
    ReservationExecutionCandidateAdapter.class,
    ReservationEscrowOrderAdapter.class,
    ReservationWalletAdapter.class,
    ReservationExecutionCancelAdapter.class,
    ReservationWeb3DisabledConfig.class,
    MarketplaceExecutionServiceConfig.class
  })
  static class BridgeConfig {

    @Bean
    ExecutionUseCases executionUseCases() {
      return new ExecutionUseCases();
    }
  }

  private static final class ExecutionUseCases
      implements GetExecutionIntentUseCase,
          GetExecutionIntentStateUseCase,
          GetExecutionIntentCandidatesUseCase,
          GetLatestExecutionIntentSummaryUseCase,
          ReplayConfirmedExecutionIntentUseCase,
          ReplayTerminatedExecutionIntentUseCase,
          CancelExecutionIntentUseCase {

    @Override
    public GetExecutionIntentResult execute(GetExecutionIntentQuery query) {
      return null;
    }

    @Override
    public GetExecutionIntentStateResult execute(GetExecutionIntentStateQuery query) {
      return null;
    }

    @Override
    public List<GetExecutionIntentCandidateResult> execute(
        GetExecutionIntentCandidatesQuery query) {
      return List.of();
    }

    @Override
    public Optional<GetLatestExecutionIntentSummaryResult> execute(
        GetLatestExecutionIntentSummaryQuery query) {
      return Optional.empty();
    }

    @Override
    public boolean execute(ReplayConfirmedExecutionIntentCommand command) {
      return false;
    }

    @Override
    public boolean execute(ReplayTerminatedExecutionIntentCommand command) {
      return false;
    }

    @Override
    public boolean cancelIfSignable(CancelExecutionIntentCommand command) {
      return false;
    }
  }
}
