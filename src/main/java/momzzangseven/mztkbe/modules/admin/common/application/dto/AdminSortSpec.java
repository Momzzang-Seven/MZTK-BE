package momzzangseven.mztkbe.modules.admin.common.application.dto;

public record AdminSortSpec(String sortKey, AdminSortDirection direction, String tieBreakerKey) {

  public AdminSortSpec {
    if (sortKey == null || sortKey.isBlank()) {
      throw new IllegalArgumentException("sortKey is required");
    }
    if (direction == null) {
      throw new IllegalArgumentException("direction is required");
    }
    if (tieBreakerKey == null || tieBreakerKey.isBlank()) {
      throw new IllegalArgumentException("tieBreakerKey is required");
    }
  }
}
