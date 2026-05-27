package momzzangseven.mztkbe.modules.web3.transaction.application.port.in.nonce;

import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.RecordSponsorNonceEvidenceCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.RecordSponsorNonceSlotTransitionCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.ReserveSponsorNonceSlotCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceEvidenceView;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceSlotReservation;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceSlotView;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.VerifyUnbroadcastableAttemptCommand;

public interface ManageNonceSlotLifecycleUseCase {

  SponsorNonceSlotReservation reserve(ReserveSponsorNonceSlotCommand command);

  SponsorNonceEvidenceView recordEvidence(RecordSponsorNonceEvidenceCommand command);

  SponsorNonceSlotView transition(RecordSponsorNonceSlotTransitionCommand command);

  boolean verifyUnbroadcastable(VerifyUnbroadcastableAttemptCommand command);

  Optional<SponsorNonceSlotView> loadSlotForReview(long chainId, String fromAddress, long nonce);

  List<SponsorNonceSlotView> loadSlotsForReview(
      long chainId, String fromAddress, int page, int size);

  List<SponsorNonceSlotView> loadSlotsForReview(long chainId, String fromAddress);
}
