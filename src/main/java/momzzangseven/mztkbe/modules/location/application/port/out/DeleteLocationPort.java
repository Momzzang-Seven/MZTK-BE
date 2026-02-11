package momzzangseven.mztkbe.modules.location.application.port.out;

import java.util.List;

/**
 * Delete Location Port (Output Port)
 *
 * <p>Location 삭제를 위한 Infrastructure Interface
 */
public interface DeleteLocationPort {
  /**
   * Location 삭제 (Hard Delete)
   *
   * @param locationId 삭제할 위치 ID
   */
  void deleteById(Long locationId);

  /**
   * 배치 삭제 (User IDs 기반, Soft Deleted location만 삭제)
   *
   * <p>Used when UserHardDeleteEventHandler is triggered.
   *
   * @param userIds User ID List to be hard-deleted
   * @return The number of deleted Location
   */
  int deleteByUserIds(List<Long> userIds);
}
