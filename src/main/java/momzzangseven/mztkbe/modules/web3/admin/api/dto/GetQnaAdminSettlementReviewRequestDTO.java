package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetQnaAdminSettlementReviewQuery;

public record GetQnaAdminSettlementReviewRequestDTO(Long postId, Long answerId) {

  public static GetQnaAdminSettlementReviewRequestDTO of(Long postId, Long answerId) {
    return new GetQnaAdminSettlementReviewRequestDTO(postId, answerId);
  }

  public GetQnaAdminSettlementReviewQuery toQuery() {
    return new GetQnaAdminSettlementReviewQuery(postId, answerId);
  }
}
