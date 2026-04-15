package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionTransactionSummary;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummariesQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummaryQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummaryResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionTransactionPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionResourceTypeCode;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetLatestExecutionIntentSummaryServiceTest {

  @Mock private ExecutionIntentPersistencePort executionIntentPersistencePort;
  @Mock private LoadExecutionTransactionPort loadExecutionTransactionPort;

  private GetLatestExecutionIntentSummaryService service;

  @BeforeEach
  void setUp() {
    service =
        new GetLatestExecutionIntentSummaryService(
            executionIntentPersistencePort, loadExecutionTransactionPort);
  }

  @Test
  void execute_returnsLatestSummaryWithTransaction() {
    ExecutionIntent intent =
        ExecutionIntent.create(
                "intent-question-1",
                "root-question-1",
                2,
                ExecutionResourceType.QUESTION,
                "101",
                ExecutionActionType.QNA_ANSWER_ACCEPT,
                7L,
                22L,
                ExecutionMode.EIP7702,
                "0x" + "a".repeat(64),
                "{\"action\":\"QNA_ANSWER_ACCEPT\"}",
                "0x" + "1".repeat(40),
                8L,
                "0x" + "2".repeat(40),
                LocalDateTime.of(2026, 4, 11, 12, 0),
                "0x" + "3".repeat(64),
                "0x" + "4".repeat(64),
                null,
                null,
                BigInteger.TEN,
                LocalDate.of(2026, 4, 11),
                LocalDateTime.of(2026, 4, 11, 11, 0))
            .markPendingOnchain(301L, LocalDateTime.of(2026, 4, 11, 11, 10));

    when(executionIntentPersistencePort.findLatestByResource(ExecutionResourceType.QUESTION, "101"))
        .thenReturn(Optional.of(intent));
    when(loadExecutionTransactionPort.findById(301L))
        .thenReturn(
            Optional.of(
                new ExecutionTransactionSummary(
                    301L, ExecutionTransactionStatus.PENDING, "0xhash301")));

    Optional<GetLatestExecutionIntentSummaryResult> result =
        service.execute(
            new GetLatestExecutionIntentSummaryQuery(ExecutionResourceTypeCode.QUESTION, "101"));

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().resourceStatus())
        .isEqualTo(ExecutionResourceStatus.PENDING_EXECUTION);
    assertThat(result.orElseThrow().executionIntentId()).isEqualTo("intent-question-1");
    assertThat(result.orElseThrow().executionIntentStatus().name()).isEqualTo("PENDING_ONCHAIN");
    assertThat(result.orElseThrow().mode().name()).isEqualTo("EIP7702");
    assertThat(result.orElseThrow().signCount()).isEqualTo(2);
    assertThat(result.orElseThrow().transactionId()).isEqualTo(301L);
    assertThat(result.orElseThrow().transactionStatus())
        .isEqualTo(ExecutionTransactionStatus.PENDING);
    assertThat(result.orElseThrow().txHash()).isEqualTo("0xhash301");
  }

  @Test
  void execute_returnsEmptyWhenLatestIntentIsMissing() {
    when(executionIntentPersistencePort.findLatestByResource(ExecutionResourceType.ANSWER, "201"))
        .thenReturn(Optional.empty());

    Optional<GetLatestExecutionIntentSummaryResult> result =
        service.execute(
            new GetLatestExecutionIntentSummaryQuery(ExecutionResourceTypeCode.ANSWER, "201"));

    assertThat(result).isEmpty();
  }

  @Test
  void executeBatch_returnsLatestSummariesWithBatchTransactionLookup() {
    ExecutionIntent intent101 =
        ExecutionIntent.create(
                "intent-answer-101",
                "root-answer-101",
                1,
                ExecutionResourceType.ANSWER,
                "101",
                ExecutionActionType.QNA_ANSWER_SUBMIT,
                7L,
                22L,
                ExecutionMode.EIP7702,
                "0x" + "a".repeat(64),
                "{\"action\":\"QNA_ANSWER_SUBMIT\"}",
                "0x" + "1".repeat(40),
                8L,
                "0x" + "2".repeat(40),
                LocalDateTime.of(2026, 4, 11, 12, 0),
                "0x" + "3".repeat(64),
                "0x" + "4".repeat(64),
                null,
                null,
                BigInteger.TEN,
                LocalDate.of(2026, 4, 11),
                LocalDateTime.of(2026, 4, 11, 11, 0))
            .markPendingOnchain(301L, LocalDateTime.of(2026, 4, 11, 11, 10));
    ExecutionIntent intent102 =
        ExecutionIntent.create(
            "intent-answer-102",
            "root-answer-102",
            1,
            ExecutionResourceType.ANSWER,
            "102",
            ExecutionActionType.QNA_ANSWER_UPDATE,
            7L,
            22L,
            ExecutionMode.EIP7702,
            "0x" + "b".repeat(64),
            "{\"action\":\"QNA_ANSWER_UPDATE\"}",
            "0x" + "5".repeat(40),
            9L,
            "0x" + "6".repeat(40),
            LocalDateTime.of(2026, 4, 11, 12, 5),
            "0x" + "7".repeat(64),
            "0x" + "8".repeat(64),
            null,
            null,
            BigInteger.TEN,
            LocalDate.of(2026, 4, 11),
            LocalDateTime.of(2026, 4, 11, 11, 5));

    when(executionIntentPersistencePort.findLatestByResources(
            eq(ExecutionResourceType.ANSWER), anyCollection()))
        .thenReturn(Map.of("101", intent101, "102", intent102));
    when(loadExecutionTransactionPort.findByIds(anyCollection()))
        .thenReturn(
            Map.of(
                301L,
                new ExecutionTransactionSummary(
                    301L, ExecutionTransactionStatus.PENDING, "0xhash301")));

    Map<String, GetLatestExecutionIntentSummaryResult> result =
        service.executeBatch(
            new GetLatestExecutionIntentSummariesQuery(
                ExecutionResourceTypeCode.ANSWER, List.of("101", "102", "103")));

    assertThat(result).hasSize(2);
    assertThat(result.get("101").transactionId()).isEqualTo(301L);
    assertThat(result.get("101").actionType()).isEqualTo(ExecutionActionType.QNA_ANSWER_SUBMIT);
    assertThat(result.get("102").transactionId()).isNull();
    assertThat(result.get("102").actionType()).isEqualTo(ExecutionActionType.QNA_ANSWER_UPDATE);
  }
}
