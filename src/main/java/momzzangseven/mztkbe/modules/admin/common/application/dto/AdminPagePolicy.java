package momzzangseven.mztkbe.modules.admin.common.application.dto;

import java.util.Set;

public record AdminPagePolicy(
    int defaultPage, int defaultSize, int maxSize, int maxSearchLength, Set<String> sortWhitelist) {

  public AdminPagePolicy {
    if (defaultPage < 0) {
      throw new IllegalArgumentException("defaultPage must be zero or positive");
    }
    if (defaultSize <= 0) {
      throw new IllegalArgumentException("defaultSize must be positive");
    }
    if (maxSize < defaultSize) {
      throw new IllegalArgumentException("maxSize must be greater than or equal to defaultSize");
    }
    if (maxSearchLength <= 0) {
      throw new IllegalArgumentException("maxSearchLength must be positive");
    }
    if (sortWhitelist == null || sortWhitelist.isEmpty()) {
      throw new IllegalArgumentException("sortWhitelist is required");
    }
    sortWhitelist = Set.copyOf(sortWhitelist);
  }
}
