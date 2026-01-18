package momzzangseven.mztkbe.modules.level.api.dto;

import lombok.Builder;
import momzzangseven.mztkbe.modules.level.application.dto.XpPolicyItem;
import momzzangseven.mztkbe.modules.level.domain.model.XpType;

@Builder
public record XpPolicyResponseDTO(XpType type, int xpAmount, int dailyCap) {

  public static XpPolicyResponseDTO from(XpPolicyItem item) {
    return XpPolicyResponseDTO.builder()
        .type(item.type())
        .xpAmount(item.xpAmount())
        .dailyCap(item.dailyCap())
        .build();
  }
}