package momzzangseven.mztkbe.modules.marketplace.classes.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetTrainerClassesQuery;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetTrainerClassesResult;

/** Use case for retrieving the authenticated trainer's own class list. */
public interface GetTrainerClassesUseCase {

  GetTrainerClassesResult execute(GetTrainerClassesQuery query);
}
