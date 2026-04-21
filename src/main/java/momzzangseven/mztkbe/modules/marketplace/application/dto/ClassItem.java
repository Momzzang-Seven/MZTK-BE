package momzzangseven.mztkbe.modules.marketplace.application.dto;

import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.domain.vo.ClassCategory;

/**
 * Projection DTO representing a single item in the class listing response.
 *
 * <p>{@code thumbnailFinalObjectKey} is populated by the image module after the DB query. {@code
 * distance} is null when location data was not provided in the query.
 */
public record ClassItem(
    Long classId,
    String title,
    ClassCategory category,
    int priceAmount,
    int durationMinutes,
    String thumbnailFinalObjectKey,
    List<String> tags,
    Double distance) {}
