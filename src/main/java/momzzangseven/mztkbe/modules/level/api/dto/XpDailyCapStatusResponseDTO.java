package momzzangseven.mztkbe.modules.level.api.dto;

import lombok.Builder;
import momzzangseven.mztkbe.modules.level.application.dto.XpDailyCapStatusItem;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;

@Builder
public record XpDailyCapStatusResponseDTO(
    XpType type, int dailyCap, int grantedCount, int remainingCount) {

  public static XpDailyCapStatusResponseDTO from(XpDailyCapStatusItem item) {
    return XpDailyCapStatusResponseDTO.builder()
        .type(item.type())
        .dailyCap(item.dailyCap())
        .grantedCount(item.grantedCount())
        .remainingCount(item.remainingCount())
        .build();
  }
}
