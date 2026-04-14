package momzzangseven.mztkbe.modules.admin.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.admin.api.dto.RecoveryReseedRequestDTO;
import momzzangseven.mztkbe.modules.admin.api.dto.RecoveryReseedResponseDTO;
import momzzangseven.mztkbe.modules.admin.application.dto.RecoveryReseedCommand;
import momzzangseven.mztkbe.modules.admin.application.dto.RecoveryReseedResult;
import momzzangseven.mztkbe.modules.admin.application.port.in.RecoveryReseedUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for break-glass recovery operations. No {@code @AdminOnly} — JWT principal is not
 * available in the recovery path.
 */
@Slf4j
@RestController
@RequestMapping("/admin/recovery")
@RequiredArgsConstructor
public class AdminRecoveryController {

  private final RecoveryReseedUseCase recoveryReseedUseCase;

  /** Reseed admin accounts after a catastrophic credential loss. */
  @PostMapping("/reseed")
  public ResponseEntity<ApiResponse<RecoveryReseedResponseDTO>> reseed(
      @Valid @RequestBody RecoveryReseedRequestDTO request, HttpServletRequest httpRequest) {
    String sourceIp = extractIp(httpRequest);
    RecoveryReseedCommand command = request.toCommand(sourceIp);
    RecoveryReseedResult result = recoveryReseedUseCase.execute(command);
    return ResponseEntity.ok(ApiResponse.success(RecoveryReseedResponseDTO.from(result)));
  }

  private String extractIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
