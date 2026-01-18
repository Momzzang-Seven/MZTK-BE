package momzzangseven.mztkbe.modules.level.api.dto;

import java.util.List;
import lombok.Builder;
import momzzangseven.mztkbe.modules.level.application.dto.LevelPoliciesResult;

@Builder
public record LevelPoliciesResponseDTO(
    List<LevelPolicyResponseDTO> levelPolicies, List<XpPolicyResponseDTO> xpPolicies) {

  public static LevelPoliciesResponseDTO from(LevelPoliciesResult result) {
    return LevelPoliciesResponseDTO.builder()
        .levelPolicies(result.levelPolicies().stream().map(LevelPolicyResponseDTO::from).toList())
        .xpPolicies(result.xpPolicies().stream().map(XpPolicyResponseDTO::from).toList())
        .build();
  }
}
