package momzzangseven.mztkbe.modules.web3.admin.api.controller;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.ForceQnaAdminRefundResponseDTO;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.ForceQnaAdminSettlementResponseDTO;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.GetQnaAdminRefundReviewResponseDTO;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.GetQnaAdminSettlementReviewResponseDTO;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.CalculateQnaAdminRefundReviewQuery;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.CalculateQnaAdminSettlementReviewQuery;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.ExecuteQnaAdminRefundCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.ExecuteQnaAdminSettlementCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.CalculateQnaAdminRefundReviewUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.CalculateQnaAdminSettlementReviewUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.ExecuteQnaAdminRefundUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.ExecuteQnaAdminSettlementUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/web3/qna/questions")
public class QnaAdminEscrowController {

  private final CalculateQnaAdminSettlementReviewUseCase calculateQnaAdminSettlementReviewUseCase;
  private final ExecuteQnaAdminSettlementUseCase executeQnaAdminSettlementUseCase;
  private final CalculateQnaAdminRefundReviewUseCase calculateQnaAdminRefundReviewUseCase;
  private final ExecuteQnaAdminRefundUseCase executeQnaAdminRefundUseCase;

  @GetMapping("/{postId}/answers/{answerId}/settlement-review")
  public ResponseEntity<ApiResponse<GetQnaAdminSettlementReviewResponseDTO>> getSettlementReview(
      @PathVariable Long postId, @PathVariable Long answerId) {
    var response =
        GetQnaAdminSettlementReviewResponseDTO.from(
            calculateQnaAdminSettlementReviewUseCase.execute(
                new CalculateQnaAdminSettlementReviewQuery(postId, answerId)));
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PostMapping("/{postId}/answers/{answerId}/settle")
  public ResponseEntity<ApiResponse<ForceQnaAdminSettlementResponseDTO>> settle(
      @AuthenticationPrincipal Long operatorId,
      @PathVariable Long postId,
      @PathVariable Long answerId) {
    var response =
        ForceQnaAdminSettlementResponseDTO.from(
            executeQnaAdminSettlementUseCase.execute(
                new ExecuteQnaAdminSettlementCommand(
                    requireOperatorId(operatorId), postId, answerId)));
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @GetMapping("/{postId}/refund-review")
  public ResponseEntity<ApiResponse<GetQnaAdminRefundReviewResponseDTO>> getRefundReview(
      @PathVariable Long postId) {
    var response =
        GetQnaAdminRefundReviewResponseDTO.from(
            calculateQnaAdminRefundReviewUseCase.execute(
                new CalculateQnaAdminRefundReviewQuery(postId)));
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PostMapping("/{postId}/refund")
  public ResponseEntity<ApiResponse<ForceQnaAdminRefundResponseDTO>> refund(
      @AuthenticationPrincipal Long operatorId, @PathVariable Long postId) {
    var response =
        ForceQnaAdminRefundResponseDTO.from(
            executeQnaAdminRefundUseCase.execute(
                new ExecuteQnaAdminRefundCommand(requireOperatorId(operatorId), postId)));
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  private Long requireOperatorId(Long operatorId) {
    if (operatorId == null) {
      throw new UserNotAuthenticatedException();
    }
    return operatorId;
  }
}
