package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletStateException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.VerifyTreasuryWalletForSignUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.DescribeKmsKeyPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pre-sign gate: combines aggregate state ({@link TreasuryWallet#assertSignable()}) with the live
 * KMS key state ({@link DescribeKmsKeyPort}). Throws {@link TreasuryWalletStateException} on either
 * failure so the caller never reaches the {@code SignDigestPort}.
 */
@Service
@RequiredArgsConstructor
public class VerifyTreasuryWalletForSignService implements VerifyTreasuryWalletForSignUseCase {

  private final LoadTreasuryWalletPort loadTreasuryWalletPort;
  private final DescribeKmsKeyPort describeKmsKeyPort;

  @Override
  @Transactional(readOnly = true)
  public void execute(String walletAlias) {
    if (walletAlias == null || walletAlias.isBlank()) {
      throw new Web3InvalidInputException("walletAlias is required");
    }
    TreasuryWallet wallet =
        loadTreasuryWalletPort
            .loadByAlias(walletAlias)
            .orElseThrow(
                () ->
                    new TreasuryWalletStateException(
                        "Treasury wallet '" + walletAlias + "' not found"));

    wallet.assertSignable();

    String kmsKeyId = wallet.getKmsKeyId();
    if (kmsKeyId == null || kmsKeyId.isBlank()) {
      throw new TreasuryWalletStateException(
          "Treasury wallet '" + walletAlias + "' has no KMS key bound");
    }

    KmsKeyState state = describeKmsKeyPort.describe(kmsKeyId);
    if (state != KmsKeyState.ENABLED) {
      throw new TreasuryWalletStateException(
          "Treasury wallet '" + walletAlias + "' KMS key is not signable: state=" + state);
    }
  }
}
