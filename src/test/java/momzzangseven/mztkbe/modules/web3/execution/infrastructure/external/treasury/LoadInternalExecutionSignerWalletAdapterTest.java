package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.treasury;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.LoadTreasuryWalletByRoleUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryKeyOrigin;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoadInternalExecutionSignerWalletAdapter")
class LoadInternalExecutionSignerWalletAdapterTest {

  @Mock private LoadTreasuryWalletByRoleUseCase loadTreasuryWalletByRoleUseCase;

  @Test
  void marketplaceAdminActionsLoadMarketplaceSigner() {
    LoadInternalExecutionSignerWalletAdapter adapter =
        new LoadInternalExecutionSignerWalletAdapter(loadTreasuryWalletByRoleUseCase);
    when(loadTreasuryWalletByRoleUseCase.execute(TreasuryRole.MARKETPLACE_SIGNER))
        .thenReturn(Optional.of(view(TreasuryRole.MARKETPLACE_SIGNER)));

    Optional<TreasuryWalletInfo> result =
        adapter.load(ExecutionActionType.MARKETPLACE_ADMIN_REFUND);

    assertThat(result).isPresent();
    assertThat(result.get().walletAlias()).isEqualTo(TreasuryRole.MARKETPLACE_SIGNER.toAlias());
    verify(loadTreasuryWalletByRoleUseCase).execute(TreasuryRole.MARKETPLACE_SIGNER);
  }

  @Test
  void qnaInternalActionsKeepLoadingSponsor() {
    LoadInternalExecutionSignerWalletAdapter adapter =
        new LoadInternalExecutionSignerWalletAdapter(loadTreasuryWalletByRoleUseCase);
    when(loadTreasuryWalletByRoleUseCase.execute(TreasuryRole.SPONSOR))
        .thenReturn(Optional.of(view(TreasuryRole.SPONSOR)));

    Optional<TreasuryWalletInfo> result = adapter.load(ExecutionActionType.QNA_ADMIN_SETTLE);

    assertThat(result).isPresent();
    assertThat(result.get().walletAlias()).isEqualTo(TreasuryRole.SPONSOR.toAlias());
    verify(loadTreasuryWalletByRoleUseCase).execute(TreasuryRole.SPONSOR);
  }

  private TreasuryWalletView view(TreasuryRole role) {
    return new TreasuryWalletView(
        role.toAlias(),
        role,
        "alias/" + role.toAlias(),
        "0x" + "a".repeat(40),
        TreasuryWalletStatus.ACTIVE,
        TreasuryKeyOrigin.IMPORTED,
        LocalDateTime.now(),
        null);
  }
}
