package momzzangseven.mztkbe.modules.web3.transfer.api.controller;

import jakarta.validation.Valid;
import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.web3.execution.api.dto.GetExecutionIntentResponseDTO;
import momzzangseven.mztkbe.modules.web3.transfer.api.dto.CreateTransferRequestDTO;
import momzzangseven.mztkbe.modules.web3.transfer.api.dto.CreateTransferResponseDTO;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.CreateTransferCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.GetTransferQuery;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.CreateTransferUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.GetTransferUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@RequestMapping("/users/me/transfers")
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class TransferController {

  private final CreateTransferUseCase createTransferUseCase;
  private final GetTransferUseCase getTransferUseCase;

  @PostMapping
  public ResponseEntity<ApiResponse<CreateTransferResponseDTO>> create(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody CreateTransferRequestDTO request) {
    userId = requireUserId(userId);

    CreateTransferCommand command =
        new CreateTransferCommand(
            userId,
            request.toUserId(),
            request.clientRequestId(),
            new BigInteger(request.amountWei()));

    return ResponseEntity.status(201)
        .body(
            ApiResponse.success(
                CreateTransferResponseDTO.from(createTransferUseCase.execute(command))));
  }

  @GetMapping("/{resourceId}")
  public ResponseEntity<ApiResponse<GetExecutionIntentResponseDTO>> get(
      @AuthenticationPrincipal Long userId, @PathVariable String resourceId) {
    userId = requireUserId(userId);

    return ResponseEntity.ok(
        ApiResponse.success(
            GetExecutionIntentResponseDTO.from(
                getTransferUseCase.execute(new GetTransferQuery(userId, resourceId)))));
  }

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
