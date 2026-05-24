package momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce;

import java.util.List;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceSlotView;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceSlot;

public interface LoadSponsorNonceSlotsPort {

  List<SponsorNonceSlot> loadOpenOrBlockingSlots(long chainId, String fromAddress);

  List<SponsorNonceSlotView> loadSlotsForReview(long chainId, String fromAddress);
}
