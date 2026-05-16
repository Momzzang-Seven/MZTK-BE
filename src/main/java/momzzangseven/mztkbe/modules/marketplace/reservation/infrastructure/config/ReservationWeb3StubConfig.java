package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.config;

import momzzangseven.mztkbe.global.error.marketplace.MarketplaceWeb3DisabledException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPaymentConfigPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionResumePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionWritePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationWalletPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.PrecheckReservationPurchasePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.PrepareReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.ReplayConfirmedReservationExecutionPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Explicit disabled stubs for marketplace user Web3 mutation paths. */
@Configuration
public class ReservationWeb3StubConfig {

  @Bean
  @ConditionalOnMissingBean(LoadReservationWalletPort.class)
  LoadReservationWalletPort disabledLoadReservationWalletPort() {
    return userId -> {
      throw new MarketplaceWeb3DisabledException();
    };
  }

  @Bean
  @ConditionalOnMissingBean(LoadReservationEscrowPaymentConfigPort.class)
  LoadReservationEscrowPaymentConfigPort disabledLoadReservationEscrowPaymentConfigPort() {
    return () -> {
      throw new MarketplaceWeb3DisabledException();
    };
  }

  @Bean
  @ConditionalOnMissingBean(LoadReservationExecutionResumePort.class)
  LoadReservationExecutionResumePort disabledLoadReservationExecutionResumePort() {
    return new LoadReservationExecutionResumePort() {
      @Override
      public java.util.Optional<
              momzzangseven.mztkbe.modules.marketplace.reservation.application.dto
                  .ReservationExecutionResumeView>
          loadLatest(Long reservationId) {
        return java.util.Optional.empty();
      }

      @Override
      public java.util.Map<
              Long,
              momzzangseven.mztkbe.modules.marketplace.reservation.application.dto
                  .ReservationExecutionResumeView>
          loadLatestBatch(java.util.Collection<Long> reservationIds) {
        return java.util.Map.of();
      }
    };
  }

  @Bean
  @ConditionalOnMissingBean(LoadReservationExecutionWritePort.class)
  LoadReservationExecutionWritePort disabledLoadReservationExecutionWritePort() {
    return (requesterUserId, executionIntentId) -> {
      throw new MarketplaceWeb3DisabledException();
    };
  }

  @Bean
  @ConditionalOnMissingBean(ReplayConfirmedReservationExecutionPort.class)
  ReplayConfirmedReservationExecutionPort disabledReplayConfirmedReservationExecutionPort() {
    return (executionIntentId, expectedActionType) -> {
      throw new MarketplaceWeb3DisabledException();
    };
  }

  @Bean
  @ConditionalOnMissingBean(PrecheckReservationPurchasePort.class)
  PrecheckReservationPurchasePort disabledPrecheckReservationPurchasePort() {
    return command -> {
      throw new MarketplaceWeb3DisabledException();
    };
  }

  @Bean
  @ConditionalOnMissingBean(PrepareReservationEscrowExecutionPort.class)
  PrepareReservationEscrowExecutionPort disabledPrepareReservationEscrowExecutionPort() {
    return new PrepareReservationEscrowExecutionPort() {
      @Override
      public PrepareReservationEscrowResult preparePurchase(
          PrepareReservationEscrowCommand command) {
        throw new MarketplaceWeb3DisabledException();
      }

      @Override
      public PrepareReservationEscrowResult prepareCancel(PrepareReservationEscrowCommand command) {
        throw new MarketplaceWeb3DisabledException();
      }

      @Override
      public PrepareReservationEscrowResult prepareConfirm(
          PrepareReservationEscrowCommand command) {
        throw new MarketplaceWeb3DisabledException();
      }

      @Override
      public PrepareReservationEscrowResult prepareDeadlineRefund(
          PrepareReservationEscrowCommand command) {
        throw new MarketplaceWeb3DisabledException();
      }
    };
  }
}
