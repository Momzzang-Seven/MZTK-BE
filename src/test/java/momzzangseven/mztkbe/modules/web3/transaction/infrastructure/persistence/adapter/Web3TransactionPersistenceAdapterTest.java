package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.level.RewardFailedOnchainException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.CreateLevelUpRewardTxIntentCommand;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxType;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.Web3TransactionEntity;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.Web3TransactionJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Web3TransactionPersistenceAdapterTest {

  @Mock private Web3TransactionJpaRepository repository;

  private Web3TransactionPersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new Web3TransactionPersistenceAdapter(repository);
  }

  @Test
  void saveLevelUpRewardIntent_throws_whenCommandNull() {
    assertThatThrownBy(() -> adapter.saveLevelUpRewardIntent(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("command is required");
  }

  @Test
  void saveLevelUpRewardIntent_throws_whenExistingFailedOnchain() {
    when(repository.findByReferenceTypeAndReferenceId(Web3ReferenceType.LEVEL_UP_REWARD, "101"))
        .thenReturn(Optional.of(existingEntity(Web3TxStatus.FAILED_ONCHAIN)));

    assertThatThrownBy(() -> adapter.saveLevelUpRewardIntent(validCommand()))
        .isInstanceOf(RewardFailedOnchainException.class);
  }

  @Test
  void saveLevelUpRewardIntent_returnsExisting_whenAlreadyPresent() {
    when(repository.findByReferenceTypeAndReferenceId(Web3ReferenceType.LEVEL_UP_REWARD, "101"))
        .thenReturn(Optional.of(existingEntity(Web3TxStatus.CREATED)));

    var tx = adapter.saveLevelUpRewardIntent(validCommand());

    assertThat(tx.getId()).isEqualTo(9L);
    assertThat(tx.getReferenceType()).isEqualTo(Web3ReferenceType.LEVEL_UP_REWARD);
    assertThat(tx.getReferenceId()).isEqualTo("101");
  }

  private CreateLevelUpRewardTxIntentCommand validCommand() {
    return new CreateLevelUpRewardTxIntentCommand(
        7L,
        101L,
        "idem-101",
        EvmAddress.of("0x5Aaeb6053f3E94C9b9A09f33669435E7Ef1BeAed"),
        EvmAddress.of("0x742d35Cc6634C0532925a3b844Bc454e4438f44e"),
        BigInteger.TEN);
  }

  private Web3TransactionEntity existingEntity(Web3TxStatus status) {
    return Web3TransactionEntity.builder()
        .id(9L)
        .idempotencyKey("idem-101")
        .referenceType(Web3ReferenceType.LEVEL_UP_REWARD)
        .referenceId("101")
        .fromUserId(null)
        .toUserId(7L)
        .fromAddress("0x" + "a".repeat(40))
        .toAddress("0x" + "b".repeat(40))
        .amountWei(BigInteger.TEN)
        .txType(Web3TxType.EIP1559)
        .status(status)
        .build();
  }
}
