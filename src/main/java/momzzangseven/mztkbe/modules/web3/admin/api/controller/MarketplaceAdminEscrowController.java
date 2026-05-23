package momzzangseven.mztkbe.modules.web3.admin.api.controller;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.config.ConditionalOnMarketplaceAdminEnabled;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.ForceMarketplaceAdminExecutionResponseDTO;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.ForceMarketplaceAdminRefundRequestDTO;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.ForceMarketplaceAdminSettlementRequestDTO;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.MarketplaceAdminEscrowReviewResponseDTO;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetMarketplaceAdminRefundReviewQuery;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetMarketplaceAdminSettlementReviewQuery;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.ForceMarketplaceAdminRefundUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.ForceMarketplaceAdminSettlementUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.GetMarketplaceAdminRefundReviewUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.GetMarketplaceAdminSettlementReviewUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/web3/marketplace/reservations")
@ConditionalOnMarketplaceAdminEnabled
public class MarketplaceAdminEscrowController {

  private final GetMarketplaceAdminRefundReviewUseCase getRefundReviewUseCase;
  private final GetMarketplaceAdminSettlementReviewUseCase getSettlementReviewUseCase;
  private final ForceMarketplaceAdminRefundUseCase forceRefundUseCase;
  private final ForceMarketplaceAdminSettlementUseCase forceSettlementUseCase;

  @GetMapping("/{reservationId}/refund-review")
  public ResponseEntity<ApiResponse<MarketplaceAdminEscrowReviewResponseDTO>> getRefundReview(
      @PathVariable Long reservationId) {
    var result =
        getRefundReviewUseCase.execute(new GetMarketplaceAdminRefundReviewQuery(reservationId));
    return ResponseEntity.ok(
        ApiResponse.success(MarketplaceAdminEscrowReviewResponseDTO.from(result.review())));
  }

  @PostMapping("/{reservationId}/refund")
  public ResponseEntity<ApiResponse<ForceMarketplaceAdminExecutionResponseDTO>> refund(
      @AuthenticationPrincipal Long operatorId,
      @PathVariable Long reservationId,
      @RequestBody ForceMarketplaceAdminRefundRequestDTO request) {
    if (request == null) {
      throw new Web3InvalidInputException("request is required");
    }
    var result =
        forceRefundUseCase.execute(request.toCommand(requireOperatorId(operatorId), reservationId));
    return ResponseEntity.ok(
        ApiResponse.success(ForceMarketplaceAdminExecutionResponseDTO.from(result.execution())));
  }

  @GetMapping("/{reservationId}/settlement-review")
  public ResponseEntity<ApiResponse<MarketplaceAdminEscrowReviewResponseDTO>> getSettlementReview(
      @PathVariable Long reservationId) {
    var result =
        getSettlementReviewUseCase.execute(
            new GetMarketplaceAdminSettlementReviewQuery(reservationId));
    return ResponseEntity.ok(
        ApiResponse.success(MarketplaceAdminEscrowReviewResponseDTO.from(result.review())));
  }

  @PostMapping("/{reservationId}/settle")
  public ResponseEntity<ApiResponse<ForceMarketplaceAdminExecutionResponseDTO>> settle(
      @AuthenticationPrincipal Long operatorId,
      @PathVariable Long reservationId,
      @RequestBody ForceMarketplaceAdminSettlementRequestDTO request) {
    if (request == null) {
      throw new Web3InvalidInputException("request is required");
    }
    var result =
        forceSettlementUseCase.execute(
            request.toCommand(requireOperatorId(operatorId), reservationId));
    return ResponseEntity.ok(
        ApiResponse.success(ForceMarketplaceAdminExecutionResponseDTO.from(result.execution())));
  }

  private Long requireOperatorId(Long operatorId) {
    if (operatorId == null) {
      throw new UserNotAuthenticatedException();
    }
    return operatorId;
  }
}
