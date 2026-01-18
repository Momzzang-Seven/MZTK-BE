package momzzangseven.mztkbe.modules.level.application.dto;

import lombok.Builder;
import momzzangseven.mztkbe.modules.level.domain.model.XpType;

/** A single XP policy row. */
@Builder
public record XpPolicyItem(XpType type, int xpAmount, int dailyCap) {}
