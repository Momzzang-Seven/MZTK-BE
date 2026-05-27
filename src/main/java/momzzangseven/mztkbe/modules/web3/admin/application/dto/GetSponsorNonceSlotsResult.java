package momzzangseven.mztkbe.modules.web3.admin.application.dto;

import java.util.List;

public record GetSponsorNonceSlotsResult(
    long chainId,
    String fromAddress,
    int page,
    int size,
    boolean hasNext,
    List<SponsorNonceSlotAdminView> slots) {

  public GetSponsorNonceSlotsResult {
    slots = List.copyOf(slots);
  }
}
