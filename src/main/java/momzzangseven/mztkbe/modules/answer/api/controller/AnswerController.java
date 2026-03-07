package momzzangseven.mztkbe.modules.answer.api.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.answer.AnswerUnauthorizedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.answer.api.dto.AnswerResponse;
import momzzangseven.mztkbe.modules.answer.api.dto.CreateAnswerRequest;
import momzzangseven.mztkbe.modules.answer.application.port.in.CreateAnswerUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.CreateAnswerUseCase.CreateAnswerCommand;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetAnswerUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/posts/{postId}/answers")
@RequiredArgsConstructor
public class AnswerController {

  private final CreateAnswerUseCase createAnswerUseCase;
  private final GetAnswerUseCase getAnswerUseCase;

  // [Create] 답변 작성 (MOM-209)
  @PostMapping
  public ResponseEntity<ApiResponse<Map<String, Long>>> createAnswer(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long postId,
      @RequestBody @Valid CreateAnswerRequest request) {

    Long validatedUserId = requireUserId(userId);
    CreateAnswerCommand command = request.toCommand(postId, validatedUserId);

    Long answerId = createAnswerUseCase.createAnswer(command);

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(Map.of("answerId", answerId)));
  }

  // [Read] 특정 게시글의 답변 목록 조회
  @GetMapping
  public ResponseEntity<ApiResponse<List<AnswerResponse>>> getAnswers(@PathVariable Long postId) {

    List<AnswerResponse> responses =
        getAnswerUseCase.getAnswersByPostId(postId).stream().map(AnswerResponse::from).toList();

    return ResponseEntity.ok(ApiResponse.success(responses));
  }

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new AnswerUnauthorizedException();
    }
    return userId;
  }
}
