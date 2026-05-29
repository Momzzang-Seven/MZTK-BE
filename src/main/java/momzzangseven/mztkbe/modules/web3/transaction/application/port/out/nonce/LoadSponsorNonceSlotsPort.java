package momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce;

import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceSlotView;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceSlot;

public interface LoadSponsorNonceSlotsPort {

  List<SponsorNonceSlot> loadOpenOrBlockingSlots(long chainId, String fromAddress);

  Optional<SponsorNonceSlotView> loadSlotForReview(long chainId, String fromAddress, long nonce);

  List<SponsorNonceSlotView> loadSlotsForReview(
      long chainId, String fromAddress, int page, int size);

  List<SponsorNonceSlotView> loadSlotsForReview(long chainId, String fromAddress);
}
