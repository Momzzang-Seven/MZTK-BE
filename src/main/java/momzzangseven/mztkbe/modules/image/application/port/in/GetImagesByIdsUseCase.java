package momzzangseven.mztkbe.modules.image.application.port.in;

import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByIdsCommand;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByIdsResult;

/** Input port for bulk-fetching image metadata by a list of image IDs. */
public interface GetImagesByIdsUseCase {
  /**
   * Returns metadata for all matching images. Images whose IDs do not exist in the database are
   * silently excluded (soft-miss). Any image that fails the 3-factor ownership check (userId /
   * referenceType / referenceId) causes the entire request to be rejected with 403.
   *
   * @param command validated request parameters
   * @return result wrapping image items ordered by the database's natural retrieval order
   */
  GetImagesByIdsResult execute(GetImagesByIdsCommand command);
}
