package momzzangseven.mztkbe.modules.web3.admin.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetSponsorNonceSlotsQuery;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetSponsorNonceSlotsResult;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.GetSponsorNonceSlotsUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.LoadSponsorNonceSlotReviewPort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetSponsorNonceSlotsService implements GetSponsorNonceSlotsUseCase {

  private final LoadSponsorNonceSlotReviewPort loadSponsorNonceSlotReviewPort;

  @Override
  public GetSponsorNonceSlotsResult execute(GetSponsorNonceSlotsQuery query) {
    if (query == null) {
      throw new Web3InvalidInputException("query is required");
    }
    query.validate();
    String normalizedAddress = query.normalizedFromAddress();
    return new GetSponsorNonceSlotsResult(
        query.chainId(),
        normalizedAddress,
        loadSponsorNonceSlotReviewPort.loadSlots(query.chainId(), normalizedAddress));
  }
}
