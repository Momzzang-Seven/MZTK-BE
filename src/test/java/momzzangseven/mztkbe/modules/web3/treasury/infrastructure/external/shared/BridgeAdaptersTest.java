package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.external.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignDigestCommand;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignDigestResult;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySignerConfigView;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.DescribeKmsKeyUseCase;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.GetRewardTreasurySignerConfigUseCase;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.GetSponsorTreasurySignerConfigUseCase;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.SignDigestUseCase;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the {@code treasury → shared} bridge adapters: covers [M-125], [M-126], [M-127],
 * [M-128].
 *
 * <p>Each adapter is a one-line delegate; tests verify (a) field-by-field propagation, (b) the
 * mapping between shared {@code SignDigestResult} ↔ shared {@link Vrs}, and (c) Optional handling
 * for the alias adapters.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Treasury 브리지 어댑터 단위 테스트")
class BridgeAdaptersTest {

  @Nested
  @DisplayName("A. DescribeKmsKeyAdapter")
  class DescribeKmsKey {

    @Mock private DescribeKmsKeyUseCase useCase;

    @Test
    @DisplayName("[M-125] describe — UseCase에 그대로 위임")
    void describe_passesThroughToUseCase() {
      when(useCase.execute("kms-id")).thenReturn(KmsKeyState.PENDING_DELETION);
      DescribeKmsKeyAdapter adapter = new DescribeKmsKeyAdapter(useCase);

      KmsKeyState result = adapter.describe("kms-id");

      assertThat(result).isSameAs(KmsKeyState.PENDING_DELETION);
      verify(useCase).execute("kms-id");
    }
  }

  @Nested
  @DisplayName("B. SignDigestAdapter")
  class SignDigest {

    @Mock private SignDigestUseCase useCase;

    @Test
    @DisplayName("[M-126] signDigest — SignDigestCommand 빌드 + Vrs로 매핑")
    void signDigest_buildsCommandAndMapsResultToVrs() {
      byte[] r = new byte[32];
      r[0] = 0x01;
      byte[] s = new byte[32];
      s[0] = 0x02;
      byte v = 28;
      when(useCase.execute(any(SignDigestCommand.class))).thenReturn(new SignDigestResult(r, s, v));
      SignDigestAdapter adapter = new SignDigestAdapter(useCase);

      ArgumentCaptor<SignDigestCommand> captor = ArgumentCaptor.forClass(SignDigestCommand.class);
      byte[] digest = new byte[32];
      digest[0] = (byte) 0xAA;

      Vrs result = adapter.signDigest("kms-id", digest, "0x" + "a".repeat(40));

      verify(useCase).execute(captor.capture());
      SignDigestCommand cmd = captor.getValue();
      assertThat(cmd.kmsKeyId()).isEqualTo("kms-id");
      assertThat(cmd.digest()).isEqualTo(digest);
      assertThat(cmd.expectedAddress()).isEqualTo("0x" + "a".repeat(40));

      assertThat(result.v()).isEqualTo(v);
      assertThat(result.r()).isEqualTo(r);
      assertThat(result.s()).isEqualTo(s);
    }
  }

  @Nested
  @DisplayName("C. RewardTreasuryAliasAdapter")
  class RewardAlias {

    @Mock private GetRewardTreasurySignerConfigUseCase useCase;

    @Test
    @DisplayName("[M-127a] loadAlias — walletAlias 존재 → Optional.of")
    void loadAlias_present_returnsOptional() {
      when(useCase.execute()).thenReturn(new TreasurySignerConfigView("reward-treasury", "kek"));
      RewardTreasuryAliasAdapter adapter = new RewardTreasuryAliasAdapter(useCase);

      Optional<String> result = adapter.loadAlias();

      assertThat(result).contains("reward-treasury");
    }

    @Test
    @DisplayName("[M-127b] loadAlias — walletAlias가 null → Optional.empty")
    void loadAlias_nullWalletAlias_returnsEmpty() {
      when(useCase.execute()).thenReturn(new TreasurySignerConfigView(null, "kek"));
      RewardTreasuryAliasAdapter adapter = new RewardTreasuryAliasAdapter(useCase);

      assertThat(adapter.loadAlias()).isEmpty();
    }
  }

  @Nested
  @DisplayName("D. SponsorTreasuryAliasAdapter")
  class SponsorAlias {

    @Mock private GetSponsorTreasurySignerConfigUseCase useCase;

    @Test
    @DisplayName("[M-128a] loadAlias — walletAlias 존재 → Optional.of")
    void loadAlias_present_returnsOptional() {
      when(useCase.execute()).thenReturn(new TreasurySignerConfigView("sponsor-treasury", "kek"));
      SponsorTreasuryAliasAdapter adapter = new SponsorTreasuryAliasAdapter(useCase);

      Optional<String> result = adapter.loadAlias();

      assertThat(result).contains("sponsor-treasury");
    }

    @Test
    @DisplayName("[M-128b] loadAlias — walletAlias가 null → Optional.empty")
    void loadAlias_nullWalletAlias_returnsEmpty() {
      when(useCase.execute()).thenReturn(new TreasurySignerConfigView(null, "kek"));
      SponsorTreasuryAliasAdapter adapter = new SponsorTreasuryAliasAdapter(useCase);

      assertThat(adapter.loadAlias()).isEmpty();
    }
  }
}
