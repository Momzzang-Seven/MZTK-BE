package momzzangseven.mztkbe.modules.location.application.dto;

import lombok.Builder;
import momzzangseven.mztkbe.global.error.location.MissingLocationInfoException;

/**
 * Command for Location deletion input parameter of Service
 *
 * @param userId
 * @param locationId
 */
@Builder
public record DeleteLocationCommand(Long userId, Long locationId) {
  /**
   * Factory Method
   *
   * @param userId
   * @param locationId
   * @return DeleteLocationCommand
   */
  public static DeleteLocationCommand of(Long userId, Long locationId) {
    return DeleteLocationCommand.builder().userId(userId).locationId(locationId).build();
  }

  public void validate() {
    if (userId == null) {
      throw new IllegalArgumentException("userId is required");
    }
    if (locationId == null) {
      throw new MissingLocationInfoException("locationId is required");
    }
  }
}
