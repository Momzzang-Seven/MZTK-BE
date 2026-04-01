package momzzangseven.mztkbe.modules.marketplace.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.application.dto.GetStoreQuery;
import momzzangseven.mztkbe.modules.marketplace.application.dto.GetStoreResult;

/** QueryHandler for retrieving a trainer's store. */
public interface GetStoreQueryHandler {

  /**
   * Execute store retrieval query.
   *
   * @param query store retrieval query
   * @return result containing the store information
   */
  GetStoreResult execute(GetStoreQuery query);
}
