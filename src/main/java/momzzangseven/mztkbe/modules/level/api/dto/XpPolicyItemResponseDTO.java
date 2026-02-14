package momzzangseven.mztkbe.modules.level.api.dto;

import lombok.Builder;
import momzzangseven.mztkbe.modules.level.application.dto.XpPolicyItem;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;

@Builder
public record XpPolicyItemResponseDTO(XpType type, int xpAmount, int dailyCap) {

  public static XpPolicyItemResponseDTO from(XpPolicyItem item) {
    return XpPolicyItemResponseDTO.builder()
        .type(item.type())
        .xpAmount(item.xpAmount())
        .dailyCap(item.dailyCap())
        .build();
  }
}
