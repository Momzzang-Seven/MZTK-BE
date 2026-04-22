package momzzangseven.mztkbe.modules.marketplace.store.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.marketplace.store.api.dto.GetStoreResponseDTO;
import momzzangseven.mztkbe.modules.marketplace.store.api.dto.UpsertStoreRequestDTO;
import momzzangseven.mztkbe.modules.marketplace.store.api.dto.UpsertStoreResponseDTO;
import momzzangseven.mztkbe.modules.marketplace.store.application.dto.GetStoreCommand;
import momzzangseven.mztkbe.modules.marketplace.store.application.dto.GetStoreResult;
import momzzangseven.mztkbe.modules.marketplace.store.application.dto.UpsertStoreResult;
import momzzangseven.mztkbe.modules.marketplace.store.application.port.in.GetStoreUseCase;
import momzzangseven.mztkbe.modules.marketplace.store.application.port.in.UpsertStoreUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/marketplace/trainer/store")
@RequiredArgsConstructor
public class StoreController {

  private final UpsertStoreUseCase upsertStoreUseCase;
  private final GetStoreUseCase getStoreUseCase;

  /**
   * Create or update a trainer store.
   *
   * <p>Always returns 200 OK regardless of whether the store was created or updated.
   */
  @PutMapping
  public ResponseEntity<ApiResponse<UpsertStoreResponseDTO>> upsertStore(
      @Valid @RequestBody UpsertStoreRequestDTO request, @AuthenticationPrincipal Long trainerId) {

    trainerId = requireUserId(trainerId);

    log.debug(
        "Store upsert request received: trainerId={}, storeName={}",
        trainerId,
        request.storeName());

    UpsertStoreResult result = upsertStoreUseCase.execute(request.toCommand(trainerId));
    UpsertStoreResponseDTO response = UpsertStoreResponseDTO.from(result);

    log.debug("Store upserted successfully: storeId={}", result.storeId());
    return ResponseEntity.ok(ApiResponse.success("Store upserted successfully", response));
  }

  /** Retrieve the current trainer's store. */
  @GetMapping
  public ResponseEntity<ApiResponse<GetStoreResponseDTO>> getStore(
      @AuthenticationPrincipal Long trainerId) {

    trainerId = requireUserId(trainerId);

    log.debug("Store retrieval request received: trainerId={}", trainerId);

    GetStoreResult result = getStoreUseCase.execute(new GetStoreCommand(trainerId));
    GetStoreResponseDTO response = GetStoreResponseDTO.from(result);

    log.debug("Store retrieved successfully: storeId={}", result.storeId());
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  // ============================================
  // Utility methods (private)
  // ============================================

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
