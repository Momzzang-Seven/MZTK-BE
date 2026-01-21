package momzzangseven.mztkbe.modules.level.api.controller;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.level.api.dto.CheckInResponseDTO;
import momzzangseven.mztkbe.modules.level.application.dto.CheckInResult;
import momzzangseven.mztkbe.modules.level.application.port.in.CheckInUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AttendanceController {

  private final CheckInUseCase checkInUseCase;

  @PostMapping("/users/me/attendance")
  public ResponseEntity<ApiResponse<CheckInResponseDTO>> checkIn(
      @AuthenticationPrincipal Long userId) {
    userId = requireUserId(userId);
    CheckInResult result = checkInUseCase.execute(userId);
    return ResponseEntity.ok(ApiResponse.success(CheckInResponseDTO.from(result)));
  }

  private Long requireUserId(Long userId) {
    if (userId == null) throw new UserNotAuthenticatedException();
    return userId;
  }
}
