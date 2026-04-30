package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link TreasuryKeyEncryptionAdapter} — covers [M-124].
 *
 * <p>The adapter is a thin pass-through to {@link TreasuryKeyCipher}; tests verify both methods
 * delegate without adding logic.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TreasuryKeyEncryptionAdapter 단위 테스트")
class TreasuryKeyEncryptionAdapterTest {

  @Mock private TreasuryKeyCipher treasuryKeyCipher;

  @InjectMocks private TreasuryKeyEncryptionAdapter adapter;

  @Test
  @DisplayName("[M-124a] encrypt — TreasuryKeyCipher.encrypt에 그대로 위임")
  void encrypt_passesThrough() {
    when(treasuryKeyCipher.encrypt("plaintext", "kek")).thenReturn("ciphertext");

    String result = adapter.encrypt("plaintext", "kek");

    assertThat(result).isEqualTo("ciphertext");
    verify(treasuryKeyCipher).encrypt("plaintext", "kek");
    verifyNoMoreInteractions(treasuryKeyCipher);
  }

  @Test
  @DisplayName("[M-124b] generateKeyB64 — TreasuryKeyCipher.generateKeyB64에 그대로 위임")
  void generateKeyB64_passesThrough() {
    when(treasuryKeyCipher.generateKeyB64()).thenReturn("base64key");

    String result = adapter.generateKeyB64();

    assertThat(result).isEqualTo("base64key");
    verify(treasuryKeyCipher).generateKeyB64();
    verifyNoMoreInteractions(treasuryKeyCipher);
  }
}
