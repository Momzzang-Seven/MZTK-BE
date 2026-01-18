package momzzangseven.mztkbe.modules.level.application.dto;

import java.util.List;
import lombok.Builder;

@Builder
public record LevelPoliciesResult(
    List<LevelPolicyItem> levelPolicies, List<XpPolicyItem> xpPolicies) {}
