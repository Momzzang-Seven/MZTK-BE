package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrecheckReservationPurchaseCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.PrecheckReservationPurchasePort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.PrecheckMarketplacePurchaseCommand;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.PrecheckMarketplacePurchaseUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Reservation infrastructure adapter for marketplace purchase precheck. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
@ConditionalOnBean(PrecheckMarketplacePurchaseUseCase.class)
public class ReservationPurchasePrecheckAdapter implements PrecheckReservationPurchasePort {

  private final PrecheckMarketplacePurchaseUseCase precheckMarketplacePurchaseUseCase;

  @Override
  public void precheckPurchase(PrecheckReservationPurchaseCommand command) {
    precheckMarketplacePurchaseUseCase.precheck(
        new PrecheckMarketplacePurchaseCommand(
            command.buyerUserId(),
            command.trainerUserId(),
            command.classId(),
            command.slotId(),
            command.signedAmount(),
            command.bookedPriceAmountKrw(),
            command.buyerWalletAddress(),
            command.trainerWalletAddress(),
            command.tokenAddress(),
            command.priceBaseUnits()));
  }
}
