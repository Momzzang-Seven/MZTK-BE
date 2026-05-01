package momzzangseven.mztkbe.modules.marketplace.classes.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetClassReservationInfoQuery;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetClassReservationInfoResult;

/** Input port for retrieving 4-week reservation availability for a single class. */
public interface GetClassReservationInfoUseCase {
  GetClassReservationInfoResult execute(GetClassReservationInfoQuery query);
}
