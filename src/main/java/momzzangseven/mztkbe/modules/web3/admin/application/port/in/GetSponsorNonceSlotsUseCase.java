package momzzangseven.mztkbe.modules.web3.admin.application.port.in;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetSponsorNonceSlotsQuery;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetSponsorNonceSlotsResult;

public interface GetSponsorNonceSlotsUseCase {

  GetSponsorNonceSlotsResult execute(GetSponsorNonceSlotsQuery query);
}
