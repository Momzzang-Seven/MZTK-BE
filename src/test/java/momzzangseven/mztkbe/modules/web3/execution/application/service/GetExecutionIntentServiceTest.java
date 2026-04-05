package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionTransactionSummary;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionChainIdPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionTransactionPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetExecutionIntentServiceTest {

  @Mock private ExecutionIntentPersistencePort executionIntentPersistencePort;
  @Mock private LoadExecutionTransactionPort loadExecutionTransactionPort;
  @Mock private LoadExecutionChainIdPort loadExecutionChainIdPort;

  private GetExecutionIntentService service;

  @BeforeEach
  void setUp() {
    service =
        new GetExecutionIntentService(
            executionIntentPersistencePort, loadExecutionTransactionPort, loadExecutionChainIdPort);
  }

  @Test
  void execute_returnsSignRequestForAwaitingSignatureEip7702() {
    ExecutionIntent intent =
        ExecutionIntent.create(
            "intent-1",
            "root-1",
            1,
            ExecutionResourceType.TRANSFER,
            "transfer:1",
            ExecutionActionType.TRANSFER_SEND,
            7L,
            8L,
            ExecutionMode.EIP7702,
            "0x" + "a".repeat(64),
            "{\"amountWei\":\"100\"}",
            "0x" + "1".repeat(40),
            12L,
            "0x" + "2".repeat(40),
            LocalDateTime.of(2026, 4, 5, 12, 0),
            "0x" + "3".repeat(64),
            "0x" + "4".repeat(64),
            null,
            null,
            BigInteger.TEN,
            LocalDate.of(2026, 4, 5),
            LocalDateTime.of(2026, 4, 5, 11, 0));

    when(executionIntentPersistencePort.findByPublicId("intent-1")).thenReturn(Optional.of(intent));
    when(loadExecutionChainIdPort.loadChainId()).thenReturn(11155111L);

    GetExecutionIntentResult result = service.execute(new GetExecutionIntentQuery(7L, "intent-1"));

    assertThat(result.resourceStatus()).isEqualTo("PENDING_EXECUTION");
    assertThat(result.signRequest()).isNotNull();
    assertThat(result.signRequest().authorization()).isNotNull();
    assertThat(result.signRequest().submit()).isNotNull();
    assertThat(result.transactionId()).isNull();
  }

  @Test
  void execute_hidesSignRequestForPendingOnchain() {
    ExecutionIntent intent =
        ExecutionIntent.create(
                "intent-2",
                "root-2",
                1,
                ExecutionResourceType.TRANSFER,
                "transfer:2",
                ExecutionActionType.TRANSFER_SEND,
                7L,
                8L,
                ExecutionMode.EIP1559,
                "0x" + "b".repeat(64),
                "{\"amountWei\":\"100\"}",
                null,
                null,
                null,
                LocalDateTime.of(2026, 4, 5, 12, 0),
                null,
                null,
                new UnsignedTxSnapshot(
                    11155111L,
                    "0x" + "5".repeat(40),
                    "0x" + "6".repeat(40),
                    BigInteger.ZERO,
                    "0x1234",
                    5L,
                    BigInteger.valueOf(80_000),
                    BigInteger.valueOf(2_000_000_000L),
                    BigInteger.valueOf(50_000_000_000L)),
                "0x" + "c".repeat(64),
                BigInteger.ZERO,
                LocalDate.of(2026, 4, 5),
                LocalDateTime.of(2026, 4, 5, 11, 0))
            .markPendingOnchain(99L);

    when(executionIntentPersistencePort.findByPublicId("intent-2")).thenReturn(Optional.of(intent));
    when(loadExecutionTransactionPort.findById(99L))
        .thenReturn(
            Optional.of(new ExecutionTransactionSummary(99L, Web3TxStatus.PENDING, "0xhash")));

    GetExecutionIntentResult result = service.execute(new GetExecutionIntentQuery(7L, "intent-2"));

    assertThat(result.resourceStatus()).isEqualTo("PENDING_EXECUTION");
    assertThat(result.signRequest()).isNull();
    assertThat(result.transactionId()).isEqualTo(99L);
    assertThat(result.transactionStatus()).isEqualTo(Web3TxStatus.PENDING);
    assertThat(result.txHash()).isEqualTo("0xhash");
  }
}
