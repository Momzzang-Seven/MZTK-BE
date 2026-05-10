package momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository;

import momzzangseven.mztkbe.modules.user.domain.model.UserRole;

/** Row projection for aggregated user statistics queries. */
public record UserStatsProjection(UserRole role, long count) {}
