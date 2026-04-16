package momzzangseven.mztkbe.modules.image.application.dto;

import java.util.List;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;

public record GetImagesStatusCommand(Long userId, List<Long> ids) {

  private static final int MAX_LOOKUP_IDS = 10;

  public void validate() {
    if (userId == null || userId <= 0) {
      throw new UserNotAuthenticatedException();
    }
    if (ids == null || ids.isEmpty()) {
      throw new IllegalArgumentException("ids must not be empty");
    }
    if (ids.size() > MAX_LOOKUP_IDS) {
      throw new IllegalArgumentException("ids must contain at most " + MAX_LOOKUP_IDS + " items");
    }
    for (Long id : ids) {
      if (id == null || id <= 0) {
        throw new IllegalArgumentException("ids must be positive");
      }
    }
  }
}
