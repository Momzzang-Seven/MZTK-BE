package momzzangseven.mztkbe.modules.web3.admin.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.ProvisionTreasuryKeyRequestDTO;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.ProvisionTreasuryKeyResponseDTO;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ProvisionTreasuryKeyCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.ProvisionTreasuryKeyUseCase;
import momzzangseven.mztkbe.modules.web3.token.application.dto.ProvisionTreasuryKeyResult;
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
public class TreasuryKeyController {

  private final ProvisionTreasuryKeyUseCase provisionTreasuryKeyUseCase;

  @PostMapping("/provision")
  public ResponseEntity<ApiResponse<ProvisionTreasuryKeyResponseDTO>> provision(
      @AuthenticationPrincipal Long operatorId,
      @Valid @RequestBody ProvisionTreasuryKeyRequestDTO request) {
    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(
            requireOperatorId(operatorId), request.treasuryPrivateKey());
    ProvisionTreasuryKeyResult result = provisionTreasuryKeyUseCase.execute(command);
    ProvisionTreasuryKeyResponseDTO response = ProvisionTreasuryKeyResponseDTO.from(result);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  private Long requireOperatorId(Long operatorId) {
    if (operatorId == null) {
      throw new UserNotAuthenticatedException();
    }
    return operatorId;
  }
}
