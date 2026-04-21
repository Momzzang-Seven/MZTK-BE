package momzzangseven.mztkbe.modules.marketplace.application.dto;

import java.util.List;

/** Result of a paginated class listing. */
public record GetClassesResult(
    List<ClassItem> items, int currentPage, int totalPages, long totalElements) {

  public static GetClassesResult of(
      List<ClassItem> items, int currentPage, int totalPages, long totalElements) {
    return new GetClassesResult(items, currentPage, totalPages, totalElements);
  }
}
