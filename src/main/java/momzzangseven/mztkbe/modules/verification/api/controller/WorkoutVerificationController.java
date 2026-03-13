package momzzangseven.mztkbe.modules.verification.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.verification.api.dto.SubmitWorkoutPhotoVerificationRequestDTO;
import momzzangseven.mztkbe.modules.verification.api.dto.SubmitWorkoutRecordVerificationRequestDTO;
import momzzangseven.mztkbe.modules.verification.api.dto.SubmitWorkoutVerificationResponseDTO;
import momzzangseven.mztkbe.modules.verification.api.dto.TodayWorkoutCompletionResponseDTO;
import momzzangseven.mztkbe.modules.verification.api.dto.VerificationDetailResponseDTO;
import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationCommand;
import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationResult;
import momzzangseven.mztkbe.modules.verification.application.dto.TodayWorkoutCompletionResult;
import momzzangseven.mztkbe.modules.verification.application.dto.VerificationDetailResult;
import momzzangseven.mztkbe.modules.verification.application.port.in.GetTodayWorkoutCompletionUseCase;
import momzzangseven.mztkbe.modules.verification.application.port.in.GetVerificationDetailUseCase;
import momzzangseven.mztkbe.modules.verification.application.port.in.SubmitWorkoutPhotoVerificationUseCase;
import momzzangseven.mztkbe.modules.verification.application.port.in.SubmitWorkoutRecordVerificationUseCase;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class WorkoutVerificationController {

  private final SubmitWorkoutPhotoVerificationUseCase submitWorkoutPhotoVerificationUseCase;
  private final SubmitWorkoutRecordVerificationUseCase submitWorkoutRecordVerificationUseCase;
  private final GetVerificationDetailUseCase getVerificationDetailUseCase;
  private final GetTodayWorkoutCompletionUseCase getTodayWorkoutCompletionUseCase;

  @PostMapping("/users/me/workout-photo-verifications")
  public ResponseEntity<ApiResponse<SubmitWorkoutVerificationResponseDTO>> submitWorkoutPhoto(
      @AuthenticationPrincipal Long userId,
      @Valid @RequestBody SubmitWorkoutPhotoVerificationRequestDTO request) {
    userId = requireUserId(userId);
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            userId, request.tmpObjectKey(), VerificationKind.WORKOUT_PHOTO);
    SubmitWorkoutVerificationResult result = submitWorkoutPhotoVerificationUseCase.execute(command);
    return ResponseEntity.ok(
        ApiResponse.success(SubmitWorkoutVerificationResponseDTO.from(result)));
  }

  @PostMapping("/users/me/workout-record-verifications")
  public ResponseEntity<ApiResponse<SubmitWorkoutVerificationResponseDTO>> submitWorkoutRecord(
      @AuthenticationPrincipal Long userId,
      @Valid @RequestBody SubmitWorkoutRecordVerificationRequestDTO request) {
    userId = requireUserId(userId);
    SubmitWorkoutVerificationCommand command =
        new SubmitWorkoutVerificationCommand(
            userId, request.tmpObjectKey(), VerificationKind.WORKOUT_RECORD);
    SubmitWorkoutVerificationResult result =
        submitWorkoutRecordVerificationUseCase.execute(command);
    return ResponseEntity.ok(
        ApiResponse.success(SubmitWorkoutVerificationResponseDTO.from(result)));
  }

  @GetMapping("/users/me/verifications/{verificationId}")
  public ResponseEntity<ApiResponse<VerificationDetailResponseDTO>> getVerificationDetail(
      @AuthenticationPrincipal Long userId, @PathVariable String verificationId) {
    userId = requireUserId(userId);
    VerificationDetailResult result = getVerificationDetailUseCase.execute(userId, verificationId);
    return ResponseEntity.ok(ApiResponse.success(VerificationDetailResponseDTO.from(result)));
  }

  @GetMapping("/users/me/workout-completion/today")
  public ResponseEntity<ApiResponse<TodayWorkoutCompletionResponseDTO>> getTodayWorkoutCompletion(
      @AuthenticationPrincipal Long userId) {
    userId = requireUserId(userId);
    TodayWorkoutCompletionResult result = getTodayWorkoutCompletionUseCase.execute(userId);
    return ResponseEntity.ok(ApiResponse.success(TodayWorkoutCompletionResponseDTO.from(result)));
  }

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
