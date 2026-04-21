package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetQnaAdminRefundReviewQuery;

public record GetQnaAdminRefundReviewRequestDTO(Long postId) {

  public static GetQnaAdminRefundReviewRequestDTO of(Long postId) {
    return new GetQnaAdminRefundReviewRequestDTO(postId);
  }

  public GetQnaAdminRefundReviewQuery toQuery() {
    return new GetQnaAdminRefundReviewQuery(postId);
  }
}
