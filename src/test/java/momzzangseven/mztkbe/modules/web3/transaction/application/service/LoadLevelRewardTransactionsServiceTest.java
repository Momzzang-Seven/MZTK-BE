package momzzangseven.mztkbe.modules.web3.transaction.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoadLevelRewardTransactionsServiceTest {

  @Mock private LoadTransactionPort loadTransactionPort;

  private LoadLevelRewardTransactionsService service;

  @BeforeEach
  void setUp() {
    service = new LoadLevelRewardTransactionsService(loadTransactionPort);
  }

  @Test
  void loadByLevelUpHistoryIds_returnsEmpty_whenInputEmpty() {
    assertThat(service.loadByLevelUpHistoryIds(null)).isEqualTo(Map.of());
    assertThat(service.loadByLevelUpHistoryIds(List.of())).isEqualTo(Map.of());
  }

  @Test
  void loadByLevelUpHistoryIds_mapsSnapshotsByReferenceId() {
    when(loadTransactionPort.loadByReferenceTypeAndReferenceIds(
            Web3ReferenceType.LEVEL_UP_REWARD, List.of("101", "102")))
        .thenReturn(
            List.of(
                new LoadTransactionPort.TransactionSnapshot(
                    1L,
                    "idem-1",
                    Web3ReferenceType.LEVEL_UP_REWARD,
                    "101",
                    null,
                    7L,
                    Web3TxStatus.PENDING,
                    "0x" + "a".repeat(64),
                    null),
                new LoadTransactionPort.TransactionSnapshot(
                    2L,
                    "idem-2",
                    Web3ReferenceType.LEVEL_UP_REWARD,
                    "102",
                    null,
                    8L,
                    Web3TxStatus.SUCCEEDED,
                    "0x" + "b".repeat(64),
                    null)));

    Map<Long, LoadLevelRewardTransactionsService.RewardTxView> result =
        service.loadByLevelUpHistoryIds(List.of(101L, 102L));

    assertThat(result).hasSize(2);
    assertThat(result.get(101L).status()).isEqualTo(Web3TxStatus.PENDING);
    assertThat(result.get(102L).txHash()).isEqualTo("0x" + "b".repeat(64));
  }

  @Test
  void loadByLevelUpHistoryIds_throws_whenSnapshotReferenceIsNotLong() {
    when(loadTransactionPort.loadByReferenceTypeAndReferenceIds(
            Web3ReferenceType.LEVEL_UP_REWARD, List.of("101")))
        .thenReturn(
            List.of(
                new LoadTransactionPort.TransactionSnapshot(
                    1L,
                    "idem-1",
                    Web3ReferenceType.LEVEL_UP_REWARD,
                    "not-a-number",
                    null,
                    7L,
                    Web3TxStatus.PENDING,
                    "0x" + "a".repeat(64),
                    null)));

    assertThatThrownBy(() -> service.loadByLevelUpHistoryIds(List.of(101L)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("invalid LEVEL_UP_REWARD referenceId");
  }
}
