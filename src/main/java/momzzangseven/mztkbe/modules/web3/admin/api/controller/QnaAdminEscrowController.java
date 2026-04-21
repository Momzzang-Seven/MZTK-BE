package momzzangseven.mztkbe.modules.web3.admin.api.controller;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.ForceQnaAdminRefundRequestDTO;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.ForceQnaAdminSettlementRequestDTO;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.ForceQnaAdminRefundResponseDTO;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.ForceQnaAdminSettlementResponseDTO;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.GetQnaAdminRefundReviewRequestDTO;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.GetQnaAdminRefundReviewResponseDTO;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.GetQnaAdminSettlementReviewRequestDTO;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.GetQnaAdminSettlementReviewResponseDTO;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.ForceQnaAdminRefundUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.ForceQnaAdminSettlementUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.GetQnaAdminRefundReviewUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.GetQnaAdminSettlementReviewUseCase;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnQnaAdminFeatureEnabled;
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
@ConditionalOnQnaAdminFeatureEnabled
public class QnaAdminEscrowController {

  private final GetQnaAdminSettlementReviewUseCase getQnaAdminSettlementReviewUseCase;
  private final ForceQnaAdminSettlementUseCase forceQnaAdminSettlementUseCase;
  private final GetQnaAdminRefundReviewUseCase getQnaAdminRefundReviewUseCase;
  private final ForceQnaAdminRefundUseCase forceQnaAdminRefundUseCase;

  @GetMapping("/{postId}/answers/{answerId}/settlement-review")
  public ResponseEntity<ApiResponse<GetQnaAdminSettlementReviewResponseDTO>> getSettlementReview(
      @PathVariable Long postId, @PathVariable Long answerId) {
    var request = GetQnaAdminSettlementReviewRequestDTO.of(postId, answerId);
    var response =
        GetQnaAdminSettlementReviewResponseDTO.from(
            getQnaAdminSettlementReviewUseCase.execute(request.toQuery()));
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PostMapping("/{postId}/answers/{answerId}/settle")
  public ResponseEntity<ApiResponse<ForceQnaAdminSettlementResponseDTO>> settle(
      @AuthenticationPrincipal Long operatorId,
      @PathVariable Long postId,
      @PathVariable Long answerId) {
    var request =
        ForceQnaAdminSettlementRequestDTO.of(requireOperatorId(operatorId), postId, answerId);
    var response =
        ForceQnaAdminSettlementResponseDTO.from(
            forceQnaAdminSettlementUseCase.execute(request.toCommand()));
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @GetMapping("/{postId}/refund-review")
  public ResponseEntity<ApiResponse<GetQnaAdminRefundReviewResponseDTO>> getRefundReview(
      @PathVariable Long postId) {
    var request = GetQnaAdminRefundReviewRequestDTO.of(postId);
    var response =
        GetQnaAdminRefundReviewResponseDTO.from(
            getQnaAdminRefundReviewUseCase.execute(request.toQuery()));
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PostMapping("/{postId}/refund")
  public ResponseEntity<ApiResponse<ForceQnaAdminRefundResponseDTO>> refund(
      @AuthenticationPrincipal Long operatorId, @PathVariable Long postId) {
    var request = ForceQnaAdminRefundRequestDTO.of(requireOperatorId(operatorId), postId);
    var response =
        ForceQnaAdminRefundResponseDTO.from(
            forceQnaAdminRefundUseCase.execute(request.toCommand()));
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  private Long requireOperatorId(Long operatorId) {
    if (operatorId == null) {
      throw new UserNotAuthenticatedException();
    }
    return operatorId;
  }
}
