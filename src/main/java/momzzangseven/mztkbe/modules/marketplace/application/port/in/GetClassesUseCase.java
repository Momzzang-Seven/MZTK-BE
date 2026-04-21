package momzzangseven.mztkbe.modules.marketplace.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.application.dto.GetClassesQuery;
import momzzangseven.mztkbe.modules.marketplace.application.dto.GetClassesResult;

/** Use case for retrieving a paginated list of marketplace classes (public endpoint). */
public interface GetClassesUseCase {

  GetClassesResult execute(GetClassesQuery query);
}
