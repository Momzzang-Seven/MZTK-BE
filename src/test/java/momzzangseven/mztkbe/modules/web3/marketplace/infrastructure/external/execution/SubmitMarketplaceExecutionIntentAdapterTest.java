package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraft;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.CreateExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionDraft;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceSignatureMeta;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceTokenMovement;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceUnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionActionType;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionResourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

@ExtendWith(MockitoExtension.class)
class SubmitMarketplaceExecutionIntentAdapterTest {

  private static final LocalDateTime EXPIRES_AT = LocalDateTime.parse("2026-04-07T12:05:00");
  private static final String ORDER_ID = "123e4567-e89b-12d3-a456-426614174000";
  private static final String ORDER_KEY = "0x" + "1".repeat(64);
  private static final String AUTHORITY = "0x" + "2".repeat(40);
  private static final String DELEGATE = "0x" + "3".repeat(40);
  private static final String ESCROW = "0x" + "4".repeat(40);
  private static final String TOKEN = "0x" + "5".repeat(40);
  private static final String CALLDATA = "0xabcdef01";

  @Mock private CreateExecutionIntentUseCase createExecutionIntentUseCase;

  @Test
  void submit_mapsMarketplaceDraftToSharedExecutionCommandAndEip7702Result() {
    SubmitMarketplaceExecutionIntentAdapter adapter =
        new SubmitMarketplaceExecutionIntentAdapter(
            createExecutionIntentUseCase, new NoOpTransactionManager());
    ArgumentCaptor<CreateExecutionIntentCommand> commandCaptor =
        ArgumentCaptor.forClass(CreateExecutionIntentCommand.class);
    MarketplaceExecutionDraft draft = eip7702Draft();
    when(createExecutionIntentUseCase.execute(any())).thenReturn(eip7702Result(true));

    MarketplaceExecutionIntentResult result = adapter.submit(draft);

    verify(createExecutionIntentUseCase).execute(commandCaptor.capture());
    ExecutionDraft submitted = commandCaptor.getValue().draft();
    assertThat(submitted.resourceType().name()).isEqualTo(draft.resourceType().name());
    assertThat(submitted.resourceId()).isEqualTo(draft.resourceId());
    assertThat(submitted.resourceStatus().name()).isEqualTo(draft.resourceStatus().name());
    assertThat(submitted.actionType().name()).isEqualTo(draft.actionType().name());
    assertThat(submitted.requesterUserId()).isEqualTo(draft.requesterUserId());
    assertThat(submitted.counterpartyUserId()).isEqualTo(draft.counterpartyUserId());
    assertThat(submitted.rootIdempotencyKey()).isEqualTo(draft.rootIdempotencyKey());
    assertThat(submitted.payloadHash()).isEqualTo(draft.payloadHash());
    assertThat(submitted.payloadSnapshotJson()).isEqualTo(draft.payloadSnapshotJson());
    assertThat(submitted.calls())
        .singleElement()
        .satisfies(
            call -> {
              assertThat(call.toAddress()).isEqualTo(TOKEN);
              assertThat(call.valueWei()).isEqualTo(BigInteger.ZERO);
              assertThat(call.data()).isEqualTo(CALLDATA);
            });
    assertThat(submitted.fallbackAllowed()).isTrue();
    assertThat(submitted.authorityAddress()).isEqualTo(AUTHORITY);
    assertThat(submitted.authorityNonce()).isEqualTo(3L);
    assertThat(submitted.delegateTarget()).isEqualTo(DELEGATE);
    assertThat(submitted.authorizationPayloadHash()).isEqualTo("0x" + "b".repeat(64));
    assertThat(submitted.unsignedTxSnapshot()).isNotNull();
    assertThat(submitted.unsignedTxFingerprint()).isEqualTo("0x" + "c".repeat(64));

    assertThat(result.resource().type()).isEqualTo("ORDER");
    assertThat(result.resource().id()).isEqualTo("order-resource-1");
    assertThat(result.resource().status()).isEqualTo("PENDING_EXECUTION");
    assertThat(result.actionType()).isEqualTo("MARKETPLACE_CLASS_PURCHASE");
    assertThat(result.orderKey()).isEqualTo(ORDER_KEY);
    assertThat(result.executionIntent().id()).isEqualTo("intent-1");
    assertThat(result.execution().mode()).isEqualTo("EIP7702");
    assertThat(result.signRequest().authorization().delegateTarget()).isEqualTo(DELEGATE);
    assertThat(result.signRequest().submit().executionDigest()).isEqualTo("0x" + "d".repeat(64));
    assertThat(result.existing()).isTrue();
    assertThat(result.signatureMeta()).isEqualTo(draft.signatureMeta());
    assertThat(result.tokenMovement()).isEqualTo(draft.tokenMovement());
  }

  @Test
  void submit_mapsEip1559SignRequest() {
    SubmitMarketplaceExecutionIntentAdapter adapter =
        new SubmitMarketplaceExecutionIntentAdapter(
            createExecutionIntentUseCase, new NoOpTransactionManager());
    when(createExecutionIntentUseCase.execute(any())).thenReturn(eip1559Result());

    MarketplaceExecutionIntentResult result = adapter.submit(eip1559Draft());

    assertThat(result.execution().mode()).isEqualTo("EIP1559");
    assertThat(result.signRequest().authorization()).isNull();
    assertThat(result.signRequest().submit()).isNull();
    assertThat(result.signRequest().transaction().fromAddress()).isEqualTo(AUTHORITY);
    assertThat(result.signRequest().transaction().toAddress()).isEqualTo(ESCROW);
    assertThat(result.signRequest().transaction().data()).isEqualTo(CALLDATA);
    assertThat(result.signRequest().transaction().expectedNonce()).isEqualTo(9L);
  }

  @Test
  void submit_retriesOnceWhenSharedRootAttemptUniqueRaceOccurs() {
    SubmitMarketplaceExecutionIntentAdapter adapter =
        new SubmitMarketplaceExecutionIntentAdapter(
            createExecutionIntentUseCase, new NoOpTransactionManager());
    MarketplaceExecutionDraft draft = eip7702Draft();
    when(createExecutionIntentUseCase.execute(any()))
        .thenThrow(new DataIntegrityViolationException("uk_web3_execution_intents_root_attempt"))
        .thenReturn(eip7702Result(true));

    MarketplaceExecutionIntentResult result = adapter.submit(draft);

    assertThat(result.existing()).isTrue();
    verify(createExecutionIntentUseCase, times(2)).execute(any());
  }

  @Test
  void submit_doesNotRetryOtherDataIntegrityFailures() {
    SubmitMarketplaceExecutionIntentAdapter adapter =
        new SubmitMarketplaceExecutionIntentAdapter(
            createExecutionIntentUseCase, new NoOpTransactionManager());
    when(createExecutionIntentUseCase.execute(any()))
        .thenThrow(new DataIntegrityViolationException("some_other_constraint"));

    assertThatThrownBy(() -> adapter.submit(eip7702Draft()))
        .isInstanceOf(DataIntegrityViolationException.class);
    verify(createExecutionIntentUseCase).execute(any());
  }

  private MarketplaceExecutionDraft eip7702Draft() {
    return new MarketplaceExecutionDraft(
        MarketplaceExecutionResourceType.ORDER,
        "order-resource-1",
        MarketplaceExecutionResourceStatus.PENDING_EXECUTION,
        MarketplaceExecutionActionType.MARKETPLACE_CLASS_PURCHASE,
        7L,
        8L,
        ORDER_ID,
        ORDER_KEY,
        "root-marketplace-1",
        "0x" + "a".repeat(64),
        "{\"payload\":true}",
        List.of(new MarketplaceExecutionDraftCall(TOKEN, BigInteger.ZERO, CALLDATA)),
        true,
        AUTHORITY,
        3L,
        DELEGATE,
        "0x" + "b".repeat(64),
        unsignedTxSnapshot(),
        "0x" + "c".repeat(64),
        new MarketplaceSignatureMeta(1_700_000_000L, 1_700_000_600L),
        new MarketplaceTokenMovement(TOKEN, BigInteger.TEN, "BUYER", AUTHORITY, "ESCROW", ESCROW),
        EXPIRES_AT);
  }

  private MarketplaceExecutionDraft eip1559Draft() {
    return new MarketplaceExecutionDraft(
        MarketplaceExecutionResourceType.ORDER,
        "order-resource-1",
        MarketplaceExecutionResourceStatus.PENDING_EXECUTION,
        MarketplaceExecutionActionType.MARKETPLACE_CLASS_CANCEL,
        7L,
        8L,
        ORDER_ID,
        ORDER_KEY,
        "root-marketplace-2",
        "0x" + "a".repeat(64),
        "{\"payload\":true}",
        List.of(new MarketplaceExecutionDraftCall(ESCROW, BigInteger.ZERO, CALLDATA)),
        true,
        null,
        null,
        null,
        null,
        unsignedTxSnapshot(),
        "0x" + "c".repeat(64),
        null,
        null,
        EXPIRES_AT);
  }

  private MarketplaceUnsignedTxSnapshot unsignedTxSnapshot() {
    return new MarketplaceUnsignedTxSnapshot(
        10L,
        AUTHORITY,
        ESCROW,
        BigInteger.ZERO,
        CALLDATA,
        9L,
        BigInteger.valueOf(100_000L),
        BigInteger.valueOf(1_000L),
        BigInteger.valueOf(2_000L));
  }

  private CreateExecutionIntentResult eip7702Result(boolean existing) {
    return new CreateExecutionIntentResult(
        ExecutionResourceType.ORDER,
        "order-resource-1",
        ExecutionResourceStatus.PENDING_EXECUTION,
        "intent-1",
        ExecutionIntentStatus.AWAITING_SIGNATURE,
        EXPIRES_AT,
        1_765_021_500L,
        ExecutionMode.EIP7702,
        2,
        SignRequestBundle.forEip7702(
            new SignRequestBundle.AuthorizationSignRequest(10L, DELEGATE, 3L, "0xauth"),
            new SignRequestBundle.SubmitSignRequest("0x" + "d".repeat(64), 1_765_021_500L)),
        existing);
  }

  private CreateExecutionIntentResult eip1559Result() {
    return new CreateExecutionIntentResult(
        ExecutionResourceType.ORDER,
        "order-resource-1",
        ExecutionResourceStatus.PENDING_EXECUTION,
        "intent-2",
        ExecutionIntentStatus.AWAITING_SIGNATURE,
        EXPIRES_AT,
        1_765_021_500L,
        ExecutionMode.EIP1559,
        1,
        SignRequestBundle.forEip1559(
            new SignRequestBundle.TransactionSignRequest(
                10L, AUTHORITY, ESCROW, "0x0", CALLDATA, 9L, "0x186a0", "0x3e8", "0x7d0", 9L)),
        false);
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
