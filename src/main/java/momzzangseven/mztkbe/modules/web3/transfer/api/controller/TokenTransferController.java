package momzzangseven.mztkbe.modules.web3.transfer.api.controller;

import jakarta.validation.Valid;
import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.web3.transfer.api.dto.request.TokenTransferPrepareRequestDTO;
import momzzangseven.mztkbe.modules.web3.transfer.api.dto.request.TokenTransferSubmitRequestDTO;
import momzzangseven.mztkbe.modules.web3.transfer.api.dto.response.TokenTransferPrepareResponseDTO;
import momzzangseven.mztkbe.modules.web3.transfer.api.dto.response.TokenTransferSubmitResponseDTO;
import momzzangseven.mztkbe.modules.web3.transfer.application.command.PrepareTokenTransferCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.command.SubmitTokenTransferCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.PrepareTokenTransferUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.SubmitTokenTransferUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.result.PrepareTokenTransferResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.result.SubmitTokenTransferResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users/me/token-transfers")
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class TokenTransferController {

  private final PrepareTokenTransferUseCase prepareTokenTransferUseCase;
  private final SubmitTokenTransferUseCase submitTokenTransferUseCase;

  @PostMapping("/prepare")
  public ResponseEntity<ApiResponse<TokenTransferPrepareResponseDTO>> prepare(
      @AuthenticationPrincipal Long userId,
      @Valid @RequestBody TokenTransferPrepareRequestDTO request) {
    assertUserId(userId);

    PrepareTokenTransferResult result =
        prepareTokenTransferUseCase.execute(
            new PrepareTokenTransferCommand(
                userId,
                request.referenceType(),
                request.referenceId(),
                request.toUserId(),
                new BigInteger(request.amountWei())));

    return ResponseEntity.status(201)
        .body(
            ApiResponse.success(
                TokenTransferPrepareResponseDTO.builder()
                    .prepareId(result.prepareId())
                    .idempotencyKey(result.idempotencyKey())
                    .txType(result.txType())
                    .authorityAddress(result.authorityAddress())
                    .authorityNonce(result.authorityNonce())
                    .delegateTarget(result.delegateTarget())
                    .authExpiresAt(result.authExpiresAt())
                    .payloadHashToSign(result.payloadHashToSign())
                    .build()));
  }

  @PostMapping("/submit")
  public ResponseEntity<ApiResponse<TokenTransferSubmitResponseDTO>> submit(
      @AuthenticationPrincipal Long userId,
      @Valid @RequestBody TokenTransferSubmitRequestDTO request) {
    assertUserId(userId);

    SubmitTokenTransferResult result =
        submitTokenTransferUseCase.execute(
            new SubmitTokenTransferCommand(
                userId,
                request.prepareId(),
                request.authorizationSignature(),
                request.executionSignature()));

    return ResponseEntity.accepted()
        .body(
            ApiResponse.success(
                TokenTransferSubmitResponseDTO.builder()
                    .transactionId(result.transactionId())
                    .status(result.status())
                    .txHash(result.txHash())
                    .build()));
  }

  private void assertUserId(Long userId) {
    if (userId == null || userId <= 0) {
      throw new Web3InvalidInputException("userId must be positive");
    }
  }
}
