package momzzangseven.mztkbe.modules.web3.execution.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraft;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.execution.application.service.CreateExecutionIntentService;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionActionTypeCode;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionResourceStatusCode;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionResourceTypeCode;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class TransactionalCreateExecutionIntentUseCaseTest {

  @Mock private CreateExecutionIntentService delegate;

  @Test
  void execute_retriesOnceWhenRootAttemptUniqueConstraintLosesRace() {
    CreateExecutionIntentCommand command = new CreateExecutionIntentCommand(draft());
    CreateExecutionIntentResult replayed = result(true);
    TransactionTemplate transactionTemplate = new TransactionTemplate(new NoOpTransactionManager());
    transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    TransactionalCreateExecutionIntentUseCase useCase =
        new TransactionalCreateExecutionIntentUseCase(delegate, transactionTemplate);
    when(delegate.execute(command))
        .thenThrow(new DataIntegrityViolationException("uk_web3_execution_intents_root_attempt"))
        .thenReturn(replayed);

    CreateExecutionIntentResult result = useCase.execute(command);

    assertThat(result).isSameAs(replayed);
    assertThat(result.existing()).isTrue();
  }

  private ExecutionDraft draft() {
    return new ExecutionDraft(
        ExecutionResourceTypeCode.TRANSFER,
        "transfer-1",
        ExecutionResourceStatusCode.PENDING_EXECUTION,
        ExecutionActionTypeCode.TRANSFER_SEND,
        7L,
        8L,
        "root-transfer-1",
        "0x" + "a".repeat(64),
        "{\"ok\":true}",
        java.util.List.of(
            new ExecutionDraftCall(
                "0x" + "1".repeat(40), java.math.BigInteger.ZERO, "0x" + "2".repeat(8))),
        true,
        "0x" + "2".repeat(40),
        1L,
        "0x" + "3".repeat(40),
        "0x" + "b".repeat(64),
        null,
        null,
        LocalDateTime.parse("2026-04-07T12:05:00"));
  }

  private CreateExecutionIntentResult result(boolean existing) {
    return new CreateExecutionIntentResult(
        ExecutionResourceType.TRANSFER,
        "transfer-1",
        ExecutionResourceStatus.PENDING_EXECUTION,
        "intent-1",
        ExecutionIntentStatus.AWAITING_SIGNATURE,
        LocalDateTime.parse("2026-04-07T12:05:00"),
        1_765_021_500L,
        ExecutionMode.EIP7702,
        2,
        SignRequestBundle.forEip7702(
            new SignRequestBundle.AuthorizationSignRequest(10L, "0xdelegate", 3L, "0xauth"),
            new SignRequestBundle.SubmitSignRequest("0xdigest", 1_765_021_500L)),
        existing);
  }

  private static class NoOpTransactionManager extends AbstractPlatformTransactionManager {

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {}

    @Override
    protected void doCommit(DefaultTransactionStatus status) {}

    @Override
    protected void doRollback(DefaultTransactionStatus status) {}
  }
}
