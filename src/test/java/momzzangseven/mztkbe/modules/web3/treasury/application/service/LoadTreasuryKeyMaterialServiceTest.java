package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.LoadTreasuryKeyMaterialQuery;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.LoadTreasuryKeyMaterialResult;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryKeyPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryKeyPort.TreasuryKeyMaterial;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link LoadTreasuryKeyMaterialService} — covers [M-94].
 *
 * <p>The service is a thin map between {@code LoadTreasuryKeyPort.TreasuryKeyMaterial} and the
 * outward-facing {@link LoadTreasuryKeyMaterialResult}; tests verify the present / absent branches.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LoadTreasuryKeyMaterialService 단위 테스트")
class LoadTreasuryKeyMaterialServiceTest {

  private static final String ALIAS = "reward-treasury";
  private static final String KEK = "dGVzdC1rZWst";
  private static final String ADDRESS = "0xDEADBEEFdeadBEEFdeadBEEFdeadBEEFdeadBEEF";
  private static final String PRIVATE_KEY_HEX = "a".repeat(64);

  @Mock private LoadTreasuryKeyPort loadTreasuryKeyPort;

  @InjectMocks private LoadTreasuryKeyMaterialService service;

  @Test
  @DisplayName("[M-94a] execute — 포트가 키를 반환하면 (treasuryAddress, privateKeyHex) 매핑")
  void execute_portReturnsKey_mapsToResult() {
    TreasuryKeyMaterial key = TreasuryKeyMaterial.of(ADDRESS, PRIVATE_KEY_HEX);
    when(loadTreasuryKeyPort.loadByAlias(ALIAS, KEK)).thenReturn(Optional.of(key));

    Optional<LoadTreasuryKeyMaterialResult> result =
        service.execute(new LoadTreasuryKeyMaterialQuery(ALIAS, KEK));

    assertThat(result).isPresent();
    LoadTreasuryKeyMaterialResult mapped = result.get();
    assertThat(mapped.treasuryAddress()).isEqualToIgnoringCase(key.treasuryAddress());
    assertThat(mapped.privateKeyHex()).isEqualTo(key.privateKeyHex());
  }

  @Test
  @DisplayName("[M-94b] execute — 포트가 비어있으면 Optional.empty")
  void execute_portReturnsEmpty_returnsEmpty() {
    when(loadTreasuryKeyPort.loadByAlias(ALIAS, KEK)).thenReturn(Optional.empty());

    Optional<LoadTreasuryKeyMaterialResult> result =
        service.execute(new LoadTreasuryKeyMaterialQuery(ALIAS, KEK));

    assertThat(result).isEmpty();
  }
}
