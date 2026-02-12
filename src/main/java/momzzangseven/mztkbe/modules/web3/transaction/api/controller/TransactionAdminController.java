package momzzangseven.mztkbe.modules.web3.transaction.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.web3.transaction.api.dto.MarkTransactionSucceededRequestDTO;
import momzzangseven.mztkbe.modules.web3.transaction.api.dto.MarkTransactionSucceededResponseDTO;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.MarkTransactionSucceededCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.MarkTransactionSucceededResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/web3/transactions")
@ConditionalOnProperty(prefix = "web3.reward-token", name = "enabled", havingValue = "true")
public class TransactionAdminController {

  private final MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  @PostMapping("/{txId}/mark-succeeded")
  public ResponseEntity<ApiResponse<MarkTransactionSucceededResponseDTO>> markSucceeded(
      @AuthenticationPrincipal Long operatorId,
      @PathVariable("txId") Long txId,
      @Valid @RequestBody MarkTransactionSucceededRequestDTO request) {
    MarkTransactionSucceededResult result =
        markTransactionSucceededUseCase.execute(
            new MarkTransactionSucceededCommand(
                operatorId,
                txId,
                request.txHash(),
                request.explorerUrl(),
                request.reason(),
                request.evidence()));

    return ResponseEntity.ok(ApiResponse.success(MarkTransactionSucceededResponseDTO.from(result)));
  }
}
