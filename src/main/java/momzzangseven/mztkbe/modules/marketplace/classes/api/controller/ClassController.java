package momzzangseven.mztkbe.modules.marketplace.classes.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.marketplace.classes.api.dto.GetClassDetailResponseDTO;
import momzzangseven.mztkbe.modules.marketplace.classes.api.dto.GetClassesResponseDTO;
import momzzangseven.mztkbe.modules.marketplace.classes.api.dto.GetTrainerClassesResponseDTO;
import momzzangseven.mztkbe.modules.marketplace.classes.api.dto.RegisterClassRequestDTO;
import momzzangseven.mztkbe.modules.marketplace.classes.api.dto.RegisterClassResponseDTO;
import momzzangseven.mztkbe.modules.marketplace.classes.api.dto.ToggleClassStatusResponseDTO;
import momzzangseven.mztkbe.modules.marketplace.classes.api.dto.UpdateClassRequestDTO;
import momzzangseven.mztkbe.modules.marketplace.classes.api.dto.UpdateClassResponseDTO;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetClassDetailQuery;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetClassesQuery;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetTrainerClassesQuery;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.ToggleClassStatusCommand;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.GetClassDetailUseCase;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.GetClassesUseCase;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.GetTrainerClassesUseCase;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.RegisterClassUseCase;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.ToggleClassStatusUseCase;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.UpdateClassUseCase;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.vo.ClassCategory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for marketplace class operations.
 *
 * <p>Exposes six endpoints:
 *
 * <ul>
 *   <li>{@code POST /marketplace/trainer/classes} — register a class (TRAINER role)
 *   <li>{@code PUT /marketplace/trainer/classes/{classId}} — update a class (TRAINER role)
 *   <li>{@code PATCH /marketplace/trainer/classes/{classId}/status} — toggle active/inactive
 *       (TRAINER role)
 *   <li>{@code GET /marketplace/trainer/classes} — trainer's own class list (TRAINER role)
 *   <li>{@code GET /marketplace/classes} — public class listing
 *   <li>{@code GET /marketplace/classes/{classId}} — public class detail
 * </ul>
 *
 * <p>Each method strictly follows the three-step pattern: (1) build command/query, (2) call use
 * case, (3) wrap result in {@link ApiResponse}.
 */
@Slf4j
@RestController
@RequestMapping("/marketplace")
@RequiredArgsConstructor
public class ClassController {

  private final RegisterClassUseCase registerClassUseCase;
  private final UpdateClassUseCase updateClassUseCase;
  private final ToggleClassStatusUseCase toggleClassStatusUseCase;
  private final GetClassesUseCase getClassesUseCase;
  private final GetClassDetailUseCase getClassDetailUseCase;
  private final GetTrainerClassesUseCase getTrainerClassesUseCase;

  // ============================================
  // TRAINER endpoints
  // ============================================

  /**
   * Register a new marketplace class.
   *
   * <p>Security: {@code /marketplace/trainer/**} is restricted to TRAINER role via SecurityConfig.
   *
   * @param request class registration payload
   * @param trainerId authenticated trainer ID from JWT
   * @return 201 Created with the new class ID
   */
  @PostMapping("/trainer/classes")
  public ResponseEntity<ApiResponse<RegisterClassResponseDTO>> registerClass(
      @Valid @RequestBody RegisterClassRequestDTO request,
      @AuthenticationPrincipal Long trainerId) {

    trainerId = requireUserId(trainerId);
    log.debug("Register class request: trainerId={}", trainerId);

    RegisterClassResponseDTO response =
        RegisterClassResponseDTO.from(registerClassUseCase.execute(request.toCommand(trainerId)));

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success("Class registered successfully", response));
  }

  /**
   * Update an existing marketplace class.
   *
   * @param classId path variable class ID
   * @param request update payload
   * @param trainerId authenticated trainer ID from JWT
   * @return 200 OK with the updated class ID
   */
  @PutMapping("/trainer/classes/{classId}")
  public ResponseEntity<ApiResponse<UpdateClassResponseDTO>> updateClass(
      @PathVariable Long classId,
      @Valid @RequestBody UpdateClassRequestDTO request,
      @AuthenticationPrincipal Long trainerId) {

    trainerId = requireUserId(trainerId);
    log.debug("Update class request: classId={}, trainerId={}", classId, trainerId);

    UpdateClassResponseDTO response =
        UpdateClassResponseDTO.from(
            updateClassUseCase.execute(request.toCommand(trainerId, classId)));

    return ResponseEntity.ok(ApiResponse.success("Class updated successfully", response));
  }

  /**
   * Toggle the active/inactive status of a class.
   *
   * @param classId class to toggle
   * @param trainerId authenticated trainer ID from JWT
   * @return 200 OK with the new status
   */
  @PatchMapping("/trainer/classes/{classId}/status")
  public ResponseEntity<ApiResponse<ToggleClassStatusResponseDTO>> toggleClassStatus(
      @PathVariable Long classId, @AuthenticationPrincipal Long trainerId) {

    trainerId = requireUserId(trainerId);
    log.debug("Toggle class status request: classId={}, trainerId={}", classId, trainerId);

    ToggleClassStatusResponseDTO response =
        ToggleClassStatusResponseDTO.from(
            toggleClassStatusUseCase.execute(new ToggleClassStatusCommand(trainerId, classId)));

    return ResponseEntity.ok(ApiResponse.success(response));
  }

  /**
   * Retrieve the authenticated trainer's own class list (active + inactive).
   *
   * @param page 0-indexed page number (default 0)
   * @param trainerId authenticated trainer ID from JWT
   * @return 200 OK with paginated trainer class list
   */
  @GetMapping("/trainer/classes")
  public ResponseEntity<ApiResponse<GetTrainerClassesResponseDTO>> getTrainerClasses(
      @RequestParam(defaultValue = "0") int page, @AuthenticationPrincipal Long trainerId) {

    trainerId = requireUserId(trainerId);
    log.debug("Get trainer classes request: trainerId={}, page={}", trainerId, page);

    GetTrainerClassesResponseDTO response =
        GetTrainerClassesResponseDTO.from(
            getTrainerClassesUseCase.execute(new GetTrainerClassesQuery(trainerId, page)));

    return ResponseEntity.ok(ApiResponse.success(response));
  }

  // ============================================
  // Public endpoints
  // ============================================

  /**
   * Retrieve a paginated list of active marketplace classes.
   *
   * <p>When {@code lat} and {@code lng} are omitted and {@code sort=DISTANCE} is requested, the
   * sort automatically falls back to RATING.
   *
   * @param lat user latitude (optional)
   * @param lng user longitude (optional)
   * @param category category filter (optional)
   * @param sort sort order (optional; default RATING)
   * @param trainerId trainer filter (optional)
   * @param startTime time range lower bound (optional, e.g. "09:00:00")
   * @param endTime time range upper bound (optional, e.g. "18:00:00")
   * @param page 0-indexed page number (default 0)
   * @return 200 OK with paginated class list
   */
  @GetMapping("/classes")
  public ResponseEntity<ApiResponse<GetClassesResponseDTO>> getClasses(
      @RequestParam(required = false) Double lat,
      @RequestParam(required = false) Double lng,
      @RequestParam(required = false) ClassCategory category,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) Long trainerId,
      @RequestParam(required = false) String startTime,
      @RequestParam(required = false) String endTime,
      @RequestParam(defaultValue = "0") int page) {

    log.debug("Get classes request: page={}, sort={}, category={}", page, sort, category);

    GetClassesResponseDTO response =
        GetClassesResponseDTO.from(
            getClassesUseCase.execute(
                new GetClassesQuery(
                    lat, lng, category, sort, trainerId, startTime, endTime, page)));

    return ResponseEntity.ok(ApiResponse.success(response));
  }

  /**
   * Retrieve the full detail of a single marketplace class.
   *
   * @param classId class ID
   * @return 200 OK with class detail
   */
  @GetMapping("/classes/{classId}")
  public ResponseEntity<ApiResponse<GetClassDetailResponseDTO>> getClassDetail(
      @PathVariable Long classId) {

    log.debug("Get class detail request: classId={}", classId);

    GetClassDetailResponseDTO response =
        GetClassDetailResponseDTO.from(
            getClassDetailUseCase.execute(new GetClassDetailQuery(classId)));

    return ResponseEntity.ok(ApiResponse.success(response));
  }

  // ============================================
  // Utility methods
  // ============================================

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
