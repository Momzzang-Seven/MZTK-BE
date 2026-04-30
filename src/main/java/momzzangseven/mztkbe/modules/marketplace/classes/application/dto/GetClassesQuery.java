package momzzangseven.mztkbe.modules.marketplace.classes.application.dto;

import momzzangseven.mztkbe.modules.marketplace.classes.domain.vo.ClassCategory;

/**
 * Query object for listing marketplace classes (public, paginated).
 *
 * <p>When {@code lat} and {@code lng} are both null, distance sort is unavailable and the service
 * falls back to RATING sort automatically.
 */
public record GetClassesQuery(
    Double lat,
    Double lng,
    ClassCategory category,
    String sort,
    Long trainerId,
    String startTime,
    String endTime,
    int page) {}
