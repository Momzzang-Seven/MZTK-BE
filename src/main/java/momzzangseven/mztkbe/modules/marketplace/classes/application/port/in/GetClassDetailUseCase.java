package momzzangseven.mztkbe.modules.marketplace.classes.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetClassDetailQuery;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetClassDetailResult;

/** Use case for retrieving the full detail of a single marketplace class. */
public interface GetClassDetailUseCase {

  GetClassDetailResult execute(GetClassDetailQuery query);
}
