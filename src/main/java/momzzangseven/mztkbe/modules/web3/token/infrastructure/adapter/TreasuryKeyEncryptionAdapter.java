package momzzangseven.mztkbe.modules.web3.token.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.TreasuryKeyEncryptionPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TreasuryKeyEncryptionAdapter implements TreasuryKeyEncryptionPort {

  private final TreasuryKeyCipher treasuryKeyCipher;

  @Override
  public String encrypt(String plaintext, String keyB64) {
    return treasuryKeyCipher.encrypt(plaintext, keyB64);
  }

  @Override
  public String generateKeyB64() {
    return treasuryKeyCipher.generateKeyB64();
  }
}
