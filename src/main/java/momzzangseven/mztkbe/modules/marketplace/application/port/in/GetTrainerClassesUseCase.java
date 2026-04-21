package momzzangseven.mztkbe.modules.marketplace.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.application.dto.GetTrainerClassesQuery;
import momzzangseven.mztkbe.modules.marketplace.application.dto.GetTrainerClassesResult;

/** Use case for retrieving the authenticated trainer's own class list. */
public interface GetTrainerClassesUseCase {

  GetTrainerClassesResult execute(GetTrainerClassesQuery query);
}
