package momzzangseven.mztkbe.modules.level.api.controller;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.level.api.dto.CheckInResponseDTO;
import momzzangseven.mztkbe.modules.level.application.dto.CheckInResult;
import momzzangseven.mztkbe.modules.level.application.dto.GetAttendanceStatusResult;
import momzzangseven.mztkbe.modules.level.application.dto.GetWeeklyAttendanceResult;
import momzzangseven.mztkbe.modules.level.application.port.in.CheckInUseCase;
import momzzangseven.mztkbe.modules.level.application.port.in.GetAttendanceStatusUseCase;
import momzzangseven.mztkbe.modules.level.application.port.in.GetWeeklyAttendanceUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AttendanceController {

  private final CheckInUseCase checkInUseCase;
  private final GetAttendanceStatusUseCase getAttendanceStatusUseCase;
  private final GetWeeklyAttendanceUseCase getWeeklyAttendanceUseCase;

  @PostMapping("/users/me/attendance")
  public ResponseEntity<ApiResponse<CheckInResponseDTO>> checkIn(
      @AuthenticationPrincipal Long userId) {
    userId = requireUserId(userId);
    CheckInResult result = checkInUseCase.execute(userId);
    return ResponseEntity.ok(ApiResponse.success(CheckInResponseDTO.from(result)));
  }

  @GetMapping("/users/me/attendance/status")
  public ResponseEntity<ApiResponse<GetAttendanceStatusResult>> getStatus(
      @AuthenticationPrincipal Long userId) {
    userId = requireUserId(userId);
    GetAttendanceStatusResult result = getAttendanceStatusUseCase.execute(userId);
    return ResponseEntity.ok(ApiResponse.success(result));
  }

  @GetMapping("/users/me/attendance/weekly")
  public ResponseEntity<ApiResponse<GetWeeklyAttendanceResult>> getWeekly(
      @AuthenticationPrincipal Long userId) {
    userId = requireUserId(userId);
    GetWeeklyAttendanceResult result = getWeeklyAttendanceUseCase.execute(userId);
    return ResponseEntity.ok(ApiResponse.success(result));
  }

  private Long requireUserId(Long userId) {
    if (userId == null) throw new UserNotAuthenticatedException();
    return userId;
  }
}
