package momzzangseven.mztkbe.modules.level.api.dto;

import java.util.List;
import lombok.Builder;
import momzzangseven.mztkbe.modules.level.application.dto.LevelPoliciesResult;

@Builder
public record GetLevelPoliciesResponseDTO(
    List<LevelPolicyItemResponseDTO> levelPolicies, List<XpPolicyItemResponseDTO> xpPolicies) {

  public static GetLevelPoliciesResponseDTO from(LevelPoliciesResult result) {
    return GetLevelPoliciesResponseDTO.builder()
        .levelPolicies(
            result.levelPolicies().stream().map(LevelPolicyItemResponseDTO::from).toList())
        .xpPolicies(result.xpPolicies().stream().map(XpPolicyItemResponseDTO::from).toList())
        .build();
  }
}
