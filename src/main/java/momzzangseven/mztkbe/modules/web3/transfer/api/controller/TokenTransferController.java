package momzzangseven.mztkbe.modules.web3.transfer.api.controller;

import jakarta.validation.Valid;
import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.web3.transfer.api.dto.TokenTransferPrepareRequestDTO;
import momzzangseven.mztkbe.modules.web3.transfer.api.dto.TokenTransferPrepareResponseDTO;
import momzzangseven.mztkbe.modules.web3.transfer.api.dto.TokenTransferSubmitRequestDTO;
import momzzangseven.mztkbe.modules.web3.transfer.api.dto.TokenTransferSubmitResponseDTO;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.PrepareTokenTransferCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.PrepareTokenTransferResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.SubmitTokenTransferCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.SubmitTokenTransferResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.PrepareTokenTransferUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.SubmitTokenTransferUseCase;
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
    userId = requireUserId(userId);

    PrepareTokenTransferCommand command =
        new PrepareTokenTransferCommand(
            userId,
            request.referenceType(),
            request.referenceId(),
            request.toUserId(),
            new BigInteger(request.amountWei()));

    PrepareTokenTransferResult result = prepareTokenTransferUseCase.execute(command);
    TokenTransferPrepareResponseDTO response = TokenTransferPrepareResponseDTO.from(result);

    return ResponseEntity.status(201).body(ApiResponse.success(response));
  }

  @PostMapping("/submit")
  public ResponseEntity<ApiResponse<TokenTransferSubmitResponseDTO>> submit(
      @AuthenticationPrincipal Long userId,
      @Valid @RequestBody TokenTransferSubmitRequestDTO request) {
    userId = requireUserId(userId);

    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(
            userId,
            request.prepareId(),
            request.authorizationSignature(),
            request.executionSignature());

    SubmitTokenTransferResult result = submitTokenTransferUseCase.execute(command);
    TokenTransferSubmitResponseDTO response = TokenTransferSubmitResponseDTO.from(result);

    return ResponseEntity.accepted().body(ApiResponse.success(response));
  }

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
