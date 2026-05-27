package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ListTreasuryWalletsService}.
 *
 * <p>The service does not validate input (no required params), is not {@code @AdminOnly}-audited,
 * and is a thin shim around {@link LoadTreasuryWalletPort#loadAll}. The tests therefore focus on
 * the two delegation branches (no filter / status filter) and the {@code TreasuryWalletView}
 * mapping.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ListTreasuryWalletsService 단위 테스트")
class ListTreasuryWalletsServiceTest {

  private static final Clock FIXED =
      Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
  private static final String ADDRESS = "0xaec2962556aa2c9c3b3e873121cb4c61ae5f1823";

  @Mock private LoadTreasuryWalletPort loadTreasuryWalletPort;

  @InjectMocks private ListTreasuryWalletsService service;

  @Nested
  @DisplayName("A. 필터 없음 (statusFilter == null)")
  class NoFilter {

    @Test
    @DisplayName("execute(null) → port.loadAll(Optional.empty()) 호출 후 View 로 매핑")
    void execute_nullFilter_delegatesWithEmptyOptionalAndMapsViews() {
      TreasuryWallet reward =
          TreasuryWallet.provision(
              TreasuryRole.REWARD.toAlias(), "kms-1", ADDRESS, TreasuryRole.REWARD, FIXED);
      TreasuryWallet sponsor =
          TreasuryWallet.provision(
              TreasuryRole.SPONSOR.toAlias(), "kms-2", ADDRESS, TreasuryRole.SPONSOR, FIXED);
      given(loadTreasuryWalletPort.loadAll(Optional.empty())).willReturn(List.of(reward, sponsor));

      List<TreasuryWalletView> result = service.execute(null);

      assertThat(result).hasSize(2);
      assertThat(result.get(0).walletAlias()).isEqualTo(TreasuryRole.REWARD.toAlias());
      assertThat(result.get(0).role()).isEqualTo(TreasuryRole.REWARD);
      assertThat(result.get(0).kmsKeyId()).isEqualTo("kms-1");
      assertThat(result.get(0).status()).isEqualTo(TreasuryWalletStatus.ACTIVE);
      assertThat(result.get(1).walletAlias()).isEqualTo(TreasuryRole.SPONSOR.toAlias());
      assertThat(result.get(1).role()).isEqualTo(TreasuryRole.SPONSOR);
    }

    @Test
    @DisplayName("execute(null) — 빈 결과면 빈 List 반환")
    void execute_nullFilter_emptyResult_returnsEmptyList() {
      given(loadTreasuryWalletPort.loadAll(Optional.empty())).willReturn(List.of());

      assertThat(service.execute(null)).isEmpty();
    }
  }

  @Nested
  @DisplayName("B. status 필터 — 모든 enum 값")
  class WithStatusFilter {

    @org.junit.jupiter.params.ParameterizedTest(
        name = "execute({0}) → port.loadAll(Optional.of({0})) 로 전달")
    @org.junit.jupiter.params.provider.EnumSource(TreasuryWalletStatus.class)
    @DisplayName("execute(status) — 모든 TreasuryWalletStatus 값에 대해 동일 enum 으로 port 위임")
    void execute_anyStatus_delegatesWithSameEnumValue(TreasuryWalletStatus status) {
      TreasuryWallet wallet =
          TreasuryWallet.provision(
              TreasuryRole.REWARD.toAlias(), "kms-1", ADDRESS, TreasuryRole.REWARD, FIXED);
      given(loadTreasuryWalletPort.loadAll(Optional.of(status))).willReturn(List.of(wallet));

      List<TreasuryWalletView> result = service.execute(status);

      assertThat(result).hasSize(1);
      // View 의 status 는 domain wallet 의 status (여기서는 항상 ACTIVE) — service 가 status 를 덮어쓰지
      // 않는다는 것까지 확인. 필터링 자체의 정합성(필터→매칭 row)은 어댑터/E2E 책임.
      assertThat(result.get(0).status()).isEqualTo(TreasuryWalletStatus.ACTIVE);
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "execute({0}) — port 결과가 비면 빈 List 반환")
    @org.junit.jupiter.params.provider.EnumSource(TreasuryWalletStatus.class)
    void execute_anyStatus_emptyPortResult_returnsEmptyList(TreasuryWalletStatus status) {
      given(loadTreasuryWalletPort.loadAll(Optional.of(status))).willReturn(List.of());

      assertThat(service.execute(status)).isEmpty();
    }
  }
}
