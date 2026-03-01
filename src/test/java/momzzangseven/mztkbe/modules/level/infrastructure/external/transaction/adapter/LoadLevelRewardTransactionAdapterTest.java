package momzzangseven.mztkbe.modules.level.infrastructure.external.transaction.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.level.application.port.out.LoadLevelRewardTransactionPort;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardTxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.LoadLevelRewardTransactionsUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoadLevelRewardTransactionAdapterTest {

  @Mock private LoadLevelRewardTransactionsUseCase loadLevelRewardTransactionsUseCase;

  @InjectMocks private LoadLevelRewardTransactionAdapter adapter;

  @Test
  void loadByLevelUpHistoryIds_shouldMapStatusesAndHashes() {
    Map<Long, LoadLevelRewardTransactionsUseCase.RewardTxView> upstream = new LinkedHashMap<>();
    upstream.put(
        10L, new LoadLevelRewardTransactionsUseCase.RewardTxView(Web3TxStatus.CREATED, null));
    upstream.put(
        11L, new LoadLevelRewardTransactionsUseCase.RewardTxView(Web3TxStatus.SUCCEEDED, "0xabc"));
    when(loadLevelRewardTransactionsUseCase.loadByLevelUpHistoryIds(List.of(10L, 11L)))
        .thenReturn(upstream);

    Map<Long, LoadLevelRewardTransactionPort.RewardTxView> result =
        adapter.loadByLevelUpHistoryIds(List.of(10L, 11L));

    assertThat(result).hasSize(2);
    assertThat(result.get(10L).status()).isEqualTo(RewardTxStatus.CREATED);
    assertThat(result.get(11L).status()).isEqualTo(RewardTxStatus.SUCCEEDED);
    assertThat(result.get(11L).txHash()).isEqualTo("0xabc");
  }
}
