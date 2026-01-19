package momzzangseven.mztkbe.modules.level.api.dto;

import java.util.List;
import lombok.Builder;
import momzzangseven.mztkbe.modules.level.application.dto.GetLevelPoliciesResult;

@Builder
public record GetLevelPoliciesResponseDTO(
    List<LevelPolicyItemResponseDTO> levelPolicies, List<XpPolicyItemResponseDTO> xpPolicies) {

  public static GetLevelPoliciesResponseDTO from(GetLevelPoliciesResult result) {
    return GetLevelPoliciesResponseDTO.builder()
        .levelPolicies(
            result.levelPolicies().stream().map(LevelPolicyItemResponseDTO::from).toList())
        .xpPolicies(result.xpPolicies().stream().map(XpPolicyItemResponseDTO::from).toList())
        .build();
  }
}
