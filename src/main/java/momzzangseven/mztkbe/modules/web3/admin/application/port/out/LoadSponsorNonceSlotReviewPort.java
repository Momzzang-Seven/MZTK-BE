package momzzangseven.mztkbe.modules.web3.admin.application.port.out;

import java.util.List;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.SponsorNonceSlotAdminView;

public interface LoadSponsorNonceSlotReviewPort {

  List<SponsorNonceSlotAdminView> loadSlots(long chainId, String fromAddress, int page, int size);
}
