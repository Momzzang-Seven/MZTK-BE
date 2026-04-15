package momzzangseven.mztkbe.modules.answer.api.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.answer.api.dto.AnswerMutationResponse;
import momzzangseven.mztkbe.modules.answer.api.dto.AnswerResponse;
import momzzangseven.mztkbe.modules.answer.api.dto.CreateAnswerRequest;
import momzzangseven.mztkbe.modules.answer.api.dto.CreateAnswerResponse;
import momzzangseven.mztkbe.modules.answer.api.dto.UpdateAnswerRequest;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerMutationResult;
import momzzangseven.mztkbe.modules.answer.application.dto.CreateAnswerCommand;
import momzzangseven.mztkbe.modules.answer.application.dto.CreateAnswerResult;
import momzzangseven.mztkbe.modules.answer.application.dto.DeleteAnswerCommand;
import momzzangseven.mztkbe.modules.answer.application.dto.RecoverAnswerEscrowCommand;
import momzzangseven.mztkbe.modules.answer.application.dto.UpdateAnswerCommand;
import momzzangseven.mztkbe.modules.answer.application.port.in.CreateAnswerUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.DeleteAnswerUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetAnswerUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.RecoverAnswerEscrowUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.UpdateAnswerUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Answer API controller.
 *
 * <p>Read responses may include owner-scoped Web3 execution summaries, while mutation responses
 * carry nullable write payloads only when a new escrow intent was prepared.
 */
@RestController
@RequestMapping("/questions/{postId}/answers")
@RequiredArgsConstructor
public class AnswerController {

  private final CreateAnswerUseCase createAnswerUseCase;
  private final GetAnswerUseCase getAnswerUseCase;
  private final UpdateAnswerUseCase updateAnswerUseCase;
  private final DeleteAnswerUseCase deleteAnswerUseCase;
  private final RecoverAnswerEscrowUseCase recoverAnswerEscrowUseCase;

  /** Creates an answer and returns nullable answer-create Web3 payload. */
  @PostMapping
  public ResponseEntity<ApiResponse<CreateAnswerResponse>> createAnswer(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long postId,
      @RequestBody @Valid CreateAnswerRequest request) {

    Long validatedUserId = requireUserId(userId);
    CreateAnswerCommand command = request.toCommand(postId, validatedUserId);
    CreateAnswerResult result = createAnswerUseCase.execute(command);

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(CreateAnswerResponse.from(result)));
  }

  /** Returns answers for an authenticated caller; owner rows may include Web3 execution summary. */
  @GetMapping
  public ResponseEntity<ApiResponse<List<AnswerResponse>>> getAnswers(
      @AuthenticationPrincipal Long userId, @PathVariable Long postId) {
    List<AnswerResponse> responses =
        getAnswerUseCase.execute(postId, userId).stream().map(AnswerResponse::from).toList();

    return ResponseEntity.ok(ApiResponse.success(responses));
  }

  /**
   * Updates an answer and returns {@code web3 = null} for local-only mutations such as image sync.
   */
  @PutMapping("/{answerId}")
  public ResponseEntity<ApiResponse<AnswerMutationResponse>> updateAnswer(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long postId,
      @PathVariable Long answerId,
      @RequestBody @Valid UpdateAnswerRequest request) {

    Long validatedUserId = requireUserId(userId);
    UpdateAnswerCommand command = request.toCommand(postId, answerId, validatedUserId);
    AnswerMutationResult result = updateAnswerUseCase.execute(command);

    return ResponseEntity.ok(ApiResponse.success(AnswerMutationResponse.from(result)));
  }

  /** Deletes an answer and returns the prepared delete escrow payload when applicable. */
  @DeleteMapping("/{answerId}")
  public ResponseEntity<ApiResponse<AnswerMutationResponse>> deleteAnswer(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long postId,
      @PathVariable Long answerId) {

    Long validatedUserId = requireUserId(userId);
    DeleteAnswerCommand command = new DeleteAnswerCommand(postId, answerId, validatedUserId);
    AnswerMutationResult result = deleteAnswerUseCase.execute(command);

    return ResponseEntity.ok(ApiResponse.success(AnswerMutationResponse.from(result)));
  }

  /**
   * Recreates an answer-submit escrow intent when the local answer exists but on-chain projection
   * is still missing after the earlier create flow terminated.
   */
  @PostMapping("/{answerId}/web3/recover-create")
  public ResponseEntity<ApiResponse<AnswerMutationResponse>> recoverAnswerCreate(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long postId,
      @PathVariable Long answerId) {

    Long validatedUserId = requireUserId(userId);
    AnswerMutationResult result =
        recoverAnswerEscrowUseCase.recoverAnswerCreate(
            new RecoverAnswerEscrowCommand(validatedUserId, postId, answerId));
    return ResponseEntity.ok(ApiResponse.success(AnswerMutationResponse.from(result)));
  }

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
