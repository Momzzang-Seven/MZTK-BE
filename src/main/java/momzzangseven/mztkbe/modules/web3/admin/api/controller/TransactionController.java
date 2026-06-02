package momzzangseven.mztkbe.modules.web3.admin.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.AdminWeb3TransactionResponseDTO;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.BulkRequeueWeb3TransactionsRequestDTO;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.BulkRequeueWeb3TransactionsResponseDTO;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.GetAdminWeb3TransactionsRequestDTO;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.MarkTransactionSucceededRequestDTO;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.MarkTransactionSucceededResponseDTO;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.RequeueWeb3TransactionRequestDTO;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.RequeueWeb3TransactionResponseDTO;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.AdminWeb3TransactionView;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.BulkRequeueAdminWeb3TransactionsResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarkTransactionSucceededCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarkTransactionSucceededResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.RequeueAdminWeb3TransactionResult;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.BulkRequeueAdminWeb3TransactionsUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.LoadAdminWeb3TransactionsUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.MarkTransactionSucceededUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.RequeueAdminWeb3TransactionUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/web3/transactions")
@ConditionalOnProperty(prefix = "web3.reward-token", name = "enabled", havingValue = "true")
public class TransactionController {

  private final MarkTransactionSucceededUseCase markTransactionSucceededUseCase;
  private final RequeueAdminWeb3TransactionUseCase requeueAdminWeb3TransactionUseCase;
  private final BulkRequeueAdminWeb3TransactionsUseCase bulkRequeueAdminWeb3TransactionsUseCase;
  private final LoadAdminWeb3TransactionsUseCase loadAdminWeb3TransactionsUseCase;

  @GetMapping
  public ResponseEntity<ApiResponse<Page<AdminWeb3TransactionResponseDTO>>> getTransactions(
      @AuthenticationPrincipal Long operatorId,
      @Valid @ModelAttribute GetAdminWeb3TransactionsRequestDTO request) {
    Page<AdminWeb3TransactionView> resultPage =
        loadAdminWeb3TransactionsUseCase.execute(request.toQuery(requireOperatorId(operatorId)));
    return ResponseEntity.ok(
        ApiResponse.success(resultPage.map(AdminWeb3TransactionResponseDTO::from)));
  }

  @PostMapping("/{txId}/mark-succeeded")
  public ResponseEntity<ApiResponse<MarkTransactionSucceededResponseDTO>> markSucceeded(
      @AuthenticationPrincipal Long operatorId,
      @PathVariable("txId") Long txId,
      @Valid @RequestBody MarkTransactionSucceededRequestDTO request) {
    MarkTransactionSucceededCommand command =
        new MarkTransactionSucceededCommand(
            requireOperatorId(operatorId),
            txId,
            request.txHash(),
            request.explorerUrl(),
            request.reason(),
            request.evidence());

    MarkTransactionSucceededResult result = markTransactionSucceededUseCase.execute(command);
    MarkTransactionSucceededResponseDTO response = MarkTransactionSucceededResponseDTO.from(result);

    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PostMapping("/{txId}/requeue")
  public ResponseEntity<ApiResponse<RequeueWeb3TransactionResponseDTO>> requeue(
      @AuthenticationPrincipal Long operatorId,
      @PathVariable("txId") Long txId,
      @Valid @RequestBody RequeueWeb3TransactionRequestDTO request) {
    RequeueAdminWeb3TransactionResult result =
        requeueAdminWeb3TransactionUseCase.execute(
            request.toCommand(requireOperatorId(operatorId), txId));
    return ResponseEntity.ok(ApiResponse.success(RequeueWeb3TransactionResponseDTO.from(result)));
  }

  @PostMapping("/requeue")
  public ResponseEntity<ApiResponse<BulkRequeueWeb3TransactionsResponseDTO>> bulkRequeue(
      @AuthenticationPrincipal Long operatorId,
      @Valid @RequestBody BulkRequeueWeb3TransactionsRequestDTO request) {
    BulkRequeueAdminWeb3TransactionsResult result =
        bulkRequeueAdminWeb3TransactionsUseCase.execute(
            request.toCommand(requireOperatorId(operatorId)));
    return ResponseEntity.ok(
        ApiResponse.success(BulkRequeueWeb3TransactionsResponseDTO.from(result)));
  }

  private Long requireOperatorId(Long operatorId) {
    if (operatorId == null) {
      throw new UserNotAuthenticatedException();
    }
    return operatorId;
  }
}
