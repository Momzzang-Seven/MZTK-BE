package momzzangseven.mztkbe.modules.web3.token.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.global.security.AdminOperatorGuard;
import momzzangseven.mztkbe.modules.web3.token.api.dto.ProvisionTreasuryKeyRequestDTO;
import momzzangseven.mztkbe.modules.web3.token.api.dto.ProvisionTreasuryKeyResponseDTO;
import momzzangseven.mztkbe.modules.web3.token.application.port.in.ProvisionTreasuryKeyUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/web3/treasury-keys")
@ConditionalOnProperty(
    prefix = "web3.reward-token.treasury.provisioning",
    name = "enabled",
    havingValue = "true")
public class TreasuryKeyAdminController {

  private final AdminOperatorGuard adminOperatorGuard;
  private final ProvisionTreasuryKeyUseCase provisionTreasuryKeyUseCase;

  @PostMapping("/provision")
  public ResponseEntity<ApiResponse<ProvisionTreasuryKeyResponseDTO>> provision(
      @AuthenticationPrincipal Long operatorId,
      @Valid @RequestBody ProvisionTreasuryKeyRequestDTO request) {
    Long requiredOperatorId = adminOperatorGuard.requireOperatorId(operatorId);
    ProvisionTreasuryKeyResponseDTO response =
        provisionTreasuryKeyUseCase.execute(requiredOperatorId, request.treasuryPrivateKey());
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}
