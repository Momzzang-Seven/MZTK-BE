package momzzangseven.mztkbe.modules.marketplace.classes.api.dto;

import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.ClassItem;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetClassesResult;

/** HTTP response DTO for the paginated class listing endpoint. */
public record GetClassesResponseDTO(
    List<ClassItemDTO> items, int currentPage, int totalPages, long totalElements) {

  public static GetClassesResponseDTO from(GetClassesResult result) {
    List<ClassItemDTO> items = result.items().stream().map(ClassItemDTO::from).toList();
    return new GetClassesResponseDTO(
        items, result.currentPage(), result.totalPages(), result.totalElements());
  }

  /** Single item in the class listing response. */
  public record ClassItemDTO(
      Long classId,
      String title,
      String category,
      int priceAmount,
      int durationMinutes,
      String thumbnailFinalObjectKey,
      List<String> tags,
      Double distance) {

    public static ClassItemDTO from(ClassItem item) {
      return new ClassItemDTO(
          item.classId(),
          item.title(),
          item.category() != null ? item.category().name() : null,
          item.priceAmount(),
          item.durationMinutes(),
          item.thumbnailFinalObjectKey(),
          item.tags(),
          item.distance());
    }
  }
}
