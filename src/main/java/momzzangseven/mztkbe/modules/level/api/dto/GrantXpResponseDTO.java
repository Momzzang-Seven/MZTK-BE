package momzzangseven.mztkbe.modules.level.api.dto;

import momzzangseven.mztkbe.modules.level.application.dto.GrantXpResult;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpStatus;
import momzzangseven.mztkbe.modules.level.domain.model.XpType;

public record GrantXpResponseDTO(
    XpType type,
    GrantXpStatus status,
    int grantedXp,
    int dailyCap,
    int grantedCountToday,
    int remainingCountToday,
    java.time.LocalDate earnedOn) {

  public static GrantXpResponseDTO from(XpType type, GrantXpResult result) {
    return new GrantXpResponseDTO(
        type,
        result.status(),
        result.grantedXp(),
        result.dailyCap(),
        result.grantedCountToday(),
        result.remainingCountToday(),
        result.earnedOn());
  }
}
