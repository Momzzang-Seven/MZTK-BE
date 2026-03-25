package momzzangseven.mztkbe.modules.image.application.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceResult.ImageItem;

/**
 * Output result of {@link
 * momzzangseven.mztkbe.modules.image.application.port.in.GetImagesByReferencesUseCase}.
 */
public record GetImagesByReferencesResult(Map<Long, List<ImageItem>> itemsByReferenceId) {

  public GetImagesByReferencesResult {
    LinkedHashMap<Long, List<ImageItem>> normalized = new LinkedHashMap<>();
    if (itemsByReferenceId != null) {
      itemsByReferenceId.forEach(
          (referenceId, items) ->
              normalized.put(referenceId, items == null ? List.of() : List.copyOf(items)));
    }
    itemsByReferenceId = Collections.unmodifiableMap(normalized);
  }

  public static GetImagesByReferencesResult of(Map<Long, List<ImageItem>> itemsByReferenceId) {
    return new GetImagesByReferencesResult(itemsByReferenceId);
  }
}
