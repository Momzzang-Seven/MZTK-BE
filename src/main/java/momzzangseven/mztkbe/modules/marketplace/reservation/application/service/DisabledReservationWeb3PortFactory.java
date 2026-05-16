package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import momzzangseven.mztkbe.global.error.marketplace.MarketplaceWeb3DisabledException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.CancelReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowOrderPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPaymentConfigPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionWritePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationWalletPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.PrecheckReservationPurchasePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.PrepareReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.ReplayConfirmedReservationExecutionPort;

final class DisabledReservationWeb3PortFactory {

  private DisabledReservationWeb3PortFactory() {}

  static LoadReservationWalletPort wallet() {
    return userId -> {
      throw new MarketplaceWeb3DisabledException();
    };
  }

  static LoadReservationEscrowPaymentConfigPort paymentConfig() {
    return () -> {
      throw new MarketplaceWeb3DisabledException();
    };
  }

  static LoadReservationEscrowOrderPort escrowOrder() {
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

  static PrecheckReservationPurchasePort precheckPurchase() {
    return command -> {
      throw new MarketplaceWeb3DisabledException();
    };
  }

  static PrepareReservationEscrowExecutionPort prepareExecution() {
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

  static CancelReservationEscrowExecutionPort cancelExecution() {
    return (executionIntentId, errorCode, errorReason) -> false;
  }

  static LoadReservationExecutionWritePort executionWrite() {
    return (requesterUserId, executionIntentId) -> {
      throw new MarketplaceWeb3DisabledException();
    };
  }

  static ReplayConfirmedReservationExecutionPort confirmedReplay() {
    return (executionIntentId, expectedActionType) -> {
      throw new MarketplaceWeb3DisabledException();
    };
  }
}
