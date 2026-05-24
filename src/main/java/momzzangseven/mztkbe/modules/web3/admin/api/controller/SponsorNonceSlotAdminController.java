package momzzangseven.mztkbe.modules.web3.admin.api.controller;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.GetSponsorNonceSlotsResponseDTO;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetSponsorNonceSlotsQuery;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetSponsorNonceSlotsResult;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.GetSponsorNonceSlotsUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/web3/nonce-slots")
public class SponsorNonceSlotAdminController {

  private final GetSponsorNonceSlotsUseCase getSponsorNonceSlotsUseCase;

  @GetMapping
  public ResponseEntity<ApiResponse<GetSponsorNonceSlotsResponseDTO>> getSlots(
      @AuthenticationPrincipal Long operatorId,
      @RequestParam Long chainId,
      @RequestParam String fromAddress) {
    GetSponsorNonceSlotsResult result =
        getSponsorNonceSlotsUseCase.execute(
            new GetSponsorNonceSlotsQuery(requireOperatorId(operatorId), chainId, fromAddress));
    return ResponseEntity.ok(ApiResponse.success(GetSponsorNonceSlotsResponseDTO.from(result)));
  }

  private Long requireOperatorId(Long operatorId) {
    if (operatorId == null) {
      throw new UserNotAuthenticatedException();
    }
    return operatorId;
  }
}
