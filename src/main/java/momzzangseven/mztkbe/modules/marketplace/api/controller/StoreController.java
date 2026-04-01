package momzzangseven.mztkbe.modules.marketplace.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.marketplace.api.dto.GetStoreResponseDTO;
import momzzangseven.mztkbe.modules.marketplace.api.dto.UpsertStoreRequestDTO;
import momzzangseven.mztkbe.modules.marketplace.api.dto.UpsertStoreResponseDTO;
import momzzangseven.mztkbe.modules.marketplace.application.dto.GetStoreQuery;
import momzzangseven.mztkbe.modules.marketplace.application.dto.GetStoreResult;
import momzzangseven.mztkbe.modules.marketplace.application.dto.UpsertStoreResult;
import momzzangseven.mztkbe.modules.marketplace.application.port.in.GetStoreQueryHandler;
import momzzangseven.mztkbe.modules.marketplace.application.port.in.UpsertStoreCommandHandler;
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

  private final UpsertStoreCommandHandler upsertStoreCommandHandler;
  private final GetStoreQueryHandler getStoreQueryHandler;

  /**
   * Create or update a trainer store.
   *
   * <p>Always returns 200 OK regardless of whether the store was created or updated, because the
   * native upsert operation is atomic and does not distinguish between the two cases.
   */
  @PutMapping
  public ResponseEntity<ApiResponse<UpsertStoreResponseDTO>> upsertStore(
      @Valid @RequestBody UpsertStoreRequestDTO request, @AuthenticationPrincipal Long trainerId) {

    requireTrainerId(trainerId);

    log.debug(
        "Store upsert request received: trainerId={}, storeName={}",
        trainerId,
        request.storeName());

    UpsertStoreResult result = upsertStoreCommandHandler.execute(request.toCommand(trainerId));
    UpsertStoreResponseDTO response = UpsertStoreResponseDTO.from(result);

    log.debug("Store upserted successfully: storeId={}", result.storeId());
    return ResponseEntity.ok(ApiResponse.success("Store upserted successfully", response));
  }

  /** Retrieve the current trainer's store. */
  @GetMapping
  public ResponseEntity<ApiResponse<GetStoreResponseDTO>> getStore(
      @AuthenticationPrincipal Long trainerId) {

    requireTrainerId(trainerId);

    log.debug("Store retrieval request received: trainerId={}", trainerId);

    GetStoreResult result = getStoreQueryHandler.execute(new GetStoreQuery(trainerId));
    GetStoreResponseDTO response = GetStoreResponseDTO.from(result);

    log.debug("Store retrieved successfully: storeId={}", result.storeId());
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  // ============================================
  // Utility methods (private)
  // ============================================

  /**
   * Safety-net check for authenticated trainer ID.
   *
   * <p>This guard exists as a defense-in-depth measure. When Spring Security is properly
   * configured, the JWT filter should guarantee a non-null principal. This method prevents
   * NullPointerException in case of security misconfiguration rather than silently propagating
   * null.
   */
  private void requireTrainerId(Long trainerId) {
    if (trainerId == null) {
      throw new UserNotAuthenticatedException();
    }
  }
}
