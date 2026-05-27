package momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce;

import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.ReserveSponsorNonceSlotCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceSlotReservation;

public interface ReserveSponsorNonceSlotPort {

  SponsorNonceSlotReservation reserve(ReserveSponsorNonceSlotCommand command);
}
