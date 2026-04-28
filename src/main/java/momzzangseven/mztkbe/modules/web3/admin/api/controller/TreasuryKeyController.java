package momzzangseven.mztkbe.modules.web3.admin.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletStateException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.ProvisionTreasuryKeyRequestDTO;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.ProvisionTreasuryKeyResponseDTO;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ProvisionTreasuryKeyCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ProvisionTreasuryKeyResult;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.ProvisionTreasuryKeyUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ArchiveTreasuryWalletCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.DisableTreasuryWalletCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.ArchiveTreasuryWalletUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.DisableTreasuryWalletUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.LoadTreasuryWalletUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
  private final LoadTreasuryWalletUseCase loadTreasuryWalletUseCase;
  private final DisableTreasuryWalletUseCase disableTreasuryWalletUseCase;
  private final ArchiveTreasuryWalletUseCase archiveTreasuryWalletUseCase;

  @PostMapping("/provision")
  public ResponseEntity<ApiResponse<ProvisionTreasuryKeyResponseDTO>> provision(
      @AuthenticationPrincipal Long operatorId,
      @Valid @RequestBody ProvisionTreasuryKeyRequestDTO request) {
    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(
            requireOperatorId(operatorId),
            request.rawPrivateKey(),
            request.role(),
            request.expectedAddress());
    ProvisionTreasuryKeyResult result = provisionTreasuryKeyUseCase.execute(command);
    return ResponseEntity.ok(
        ApiResponse.success(ProvisionTreasuryKeyResponseDTO.from(result)));
  }

  @GetMapping("/{walletAlias}")
  public ResponseEntity<ApiResponse<TreasuryWalletView>> get(
      @AuthenticationPrincipal Long operatorId, @PathVariable String walletAlias) {
    requireOperatorId(operatorId);
    TreasuryWalletView view =
        loadTreasuryWalletUseCase
            .execute(walletAlias)
            .orElseThrow(
                () ->
                    new TreasuryWalletStateException(
                        "Treasury wallet '" + walletAlias + "' not found"));
    return ResponseEntity.ok(ApiResponse.success(view));
  }

  @PostMapping("/{walletAlias}/disable")
  public ResponseEntity<ApiResponse<TreasuryWalletView>> disable(
      @AuthenticationPrincipal Long operatorId, @PathVariable String walletAlias) {
    TreasuryWalletView view =
        disableTreasuryWalletUseCase.execute(
            new DisableTreasuryWalletCommand(walletAlias, requireOperatorId(operatorId)));
    return ResponseEntity.ok(ApiResponse.success(view));
  }

  @PostMapping("/{walletAlias}/archive")
  public ResponseEntity<ApiResponse<TreasuryWalletView>> archive(
      @AuthenticationPrincipal Long operatorId, @PathVariable String walletAlias) {
    TreasuryWalletView view =
        archiveTreasuryWalletUseCase.execute(
            new ArchiveTreasuryWalletCommand(walletAlias, requireOperatorId(operatorId)));
    return ResponseEntity.ok(ApiResponse.success(view));
  }

  private Long requireOperatorId(Long operatorId) {
    if (operatorId == null) {
      throw new UserNotAuthenticatedException();
    }
    return operatorId;
  }
}
