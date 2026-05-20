package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.config;

import momzzangseven.mztkbe.global.error.marketplace.MarketplaceWeb3DisabledException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.CancelReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowOrderPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPaymentConfigPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionCandidatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionResumePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionWritePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationWalletPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.PrecheckReservationPurchasePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.PrepareReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.ReplayConfirmedReservationExecutionPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Explicit disabled stubs for marketplace user Web3 mutation paths. */
@Configuration
public class ReservationWeb3StubConfig {

  @Bean
  LoadReservationWalletPort disabledLoadReservationWalletPort() {
    return userId -> {
      throw new MarketplaceWeb3DisabledException();
    };
  }

  @Bean
  LoadReservationEscrowPaymentConfigPort disabledLoadReservationEscrowPaymentConfigPort() {
    return () -> {
      throw new MarketplaceWeb3DisabledException();
    };
  }

  @Bean
  LoadReservationEscrowOrderPort disabledLoadReservationEscrowOrderPort() {
    return new LoadReservationEscrowOrderPort() {
      @Override
      public momzzangseven.mztkbe.modules.marketplace.reservation.application.dto
              .ReservationEscrowOrderView
          getOrder(String orderKey) {
        throw new MarketplaceWeb3DisabledException();
      }

      @Override
      public java.util.List<
              momzzangseven.mztkbe.modules.marketplace.reservation.application.dto
                  .ReservationEscrowOrderView>
          getOrders(java.util.Collection<String> orderKeys) {
        throw new MarketplaceWeb3DisabledException();
      }
    };
  }

  @Bean
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
  CancelReservationEscrowExecutionPort disabledCancelReservationEscrowExecutionPort() {
    return (executionIntentId, errorCode, errorReason) -> false;
  }

  @Bean
  LoadReservationExecutionWritePort disabledLoadReservationExecutionWritePort() {
    return (requesterUserId, executionIntentId) -> {
      throw new MarketplaceWeb3DisabledException();
    };
  }

  @Bean
  LoadReservationExecutionStatePort disabledLoadReservationExecutionStatePort() {
    return executionIntentId -> {
      throw new MarketplaceWeb3DisabledException();
    };
  }

  @Bean
  ReplayConfirmedReservationExecutionPort disabledReplayConfirmedReservationExecutionPort() {
    return (executionIntentId, expectedActionType) -> {
      throw new MarketplaceWeb3DisabledException();
    };
  }

  @Bean
  LoadReservationExecutionCandidatePort disabledLoadReservationExecutionCandidatePort() {
    return reservationId -> java.util.List.of();
  }

  @Bean
  PrecheckReservationPurchasePort disabledPrecheckReservationPurchasePort() {
    return command -> {
      throw new MarketplaceWeb3DisabledException();
    };
  }

  @Bean
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
