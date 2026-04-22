package momzzangseven.mztkbe.modules.marketplace.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.application.dto.GetClassReservationInfoQuery;
import momzzangseven.mztkbe.modules.marketplace.application.dto.GetClassReservationInfoResult;

/** Input port for retrieving 4-week reservation availability for a single class. */
public interface GetClassReservationInfoUseCase {
  GetClassReservationInfoResult execute(GetClassReservationInfoQuery query);
}
