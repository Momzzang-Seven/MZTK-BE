package momzzangseven.mztkbe.modules.web3.admin.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;

public record GetSponsorNonceSlotsQuery(
    Long operatorId, Long chainId, String fromAddress, Integer page, Integer size) {

  private static final int DEFAULT_PAGE = 0;
  private static final int DEFAULT_SIZE = 100;
  private static final int MAX_SIZE = 500;

  public void validate() {
    if (operatorId == null || operatorId <= 0) {
      throw new Web3InvalidInputException("operatorId must be positive");
    }
    if (chainId == null || chainId <= 0) {
      throw new Web3InvalidInputException("chainId must be positive");
    }
    EvmAddress.of(fromAddress);
    if (normalizedPage() < 0) {
      throw new Web3InvalidInputException("page must be zero or positive");
    }
    if (normalizedSize() <= 0 || normalizedSize() > MAX_SIZE) {
      throw new Web3InvalidInputException("size must be between 1 and " + MAX_SIZE);
    }
  }

  public String normalizedFromAddress() {
    return EvmAddress.of(fromAddress).value();
  }

  public int normalizedPage() {
    return page == null ? DEFAULT_PAGE : page;
  }

  public int normalizedSize() {
    return size == null ? DEFAULT_SIZE : size;
  }
}
