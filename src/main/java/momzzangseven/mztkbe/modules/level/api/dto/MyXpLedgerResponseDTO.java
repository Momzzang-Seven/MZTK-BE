package momzzangseven.mztkbe.modules.level.api.dto;

import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import momzzangseven.mztkbe.modules.level.application.dto.MyXpLedgerResult;

@Builder
public record MyXpLedgerResponseDTO(
    int page,
    int size,
    boolean hasNext,
    LocalDate earnedOn,
    List<XpLedgerEntryResponseDTO> entries,
    List<XpDailyCapStatusResponseDTO> todayCaps) {

  public static MyXpLedgerResponseDTO from(MyXpLedgerResult result) {
    return MyXpLedgerResponseDTO.builder()
        .page(result.page())
        .size(result.size())
        .hasNext(result.hasNext())
        .earnedOn(result.earnedOn())
        .entries(result.entries().stream().map(XpLedgerEntryResponseDTO::from).toList())
        .todayCaps(result.todayCaps().stream().map(XpDailyCapStatusResponseDTO::from).toList())
        .build();
  }
}
