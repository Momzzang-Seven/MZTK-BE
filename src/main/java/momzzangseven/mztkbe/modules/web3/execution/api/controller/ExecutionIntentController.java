package momzzangseven.mztkbe.modules.web3.execution.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.web3.execution.api.dto.ExecuteExecutionIntentRequestDTO;
import momzzangseven.mztkbe.modules.web3.execution.api.dto.ExecuteExecutionIntentResponseDTO;
import momzzangseven.mztkbe.modules.web3.execution.api.dto.GetExecutionIntentResponseDTO;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionIntentUseCase;
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
@RequestMapping("/users/me/web3/execution-intents")
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class ExecutionIntentController {

  private final GetExecutionIntentUseCase getExecutionIntentUseCase;
  private final ExecuteExecutionIntentUseCase executeExecutionIntentUseCase;

  @GetMapping("/{executionIntentId}")
  public ResponseEntity<ApiResponse<GetExecutionIntentResponseDTO>> getExecutionIntent(
      @AuthenticationPrincipal Long userId, @PathVariable String executionIntentId) {
    userId = requireUserId(userId);

    GetExecutionIntentResult result =
        getExecutionIntentUseCase.execute(new GetExecutionIntentQuery(userId, executionIntentId));

    return ResponseEntity.ok(ApiResponse.success(GetExecutionIntentResponseDTO.from(result)));
  }

  @PostMapping("/{executionIntentId}/execute")
  public ResponseEntity<ApiResponse<ExecuteExecutionIntentResponseDTO>> executeExecutionIntent(
      @AuthenticationPrincipal Long userId,
      @PathVariable String executionIntentId,
      @Valid @RequestBody ExecuteExecutionIntentRequestDTO request) {
    userId = requireUserId(userId);

    ExecuteExecutionIntentResult result =
        executeExecutionIntentUseCase.execute(
            new ExecuteExecutionIntentCommand(
                userId,
                executionIntentId,
                request.authorizationSignature(),
                request.submitSignature(),
                request.signedRawTransaction()));

    return ResponseEntity.accepted()
        .body(ApiResponse.success(ExecuteExecutionIntentResponseDTO.from(result)));
  }

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
