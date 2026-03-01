package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransferException;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.LoadTreasuryKeyPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.ReserveNoncePort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.TransferTransactionPersistencePort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.TransferTransaction;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxType;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.SubmitTokenTransferCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.SubmitTokenTransferResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.Eip7702AuthorizationPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.Eip7702ChainPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.Eip7702TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.LoadTransferRuntimeConfigPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.QuestionRewardIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.TransferPreparePersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.VerifyExecutionSignaturePort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntent;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TokenTransferIdempotencyKeyFactory;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TokenTransferReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TransferPrepare;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TransferPrepareStatus;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferRuntimeConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubmitTokenTransferServiceTest {

  @Mock private TransferPreparePersistencePort transferPreparePersistencePort;
  @Mock private TransferTransactionPersistencePort transferTransactionPersistencePort;
  @Mock private SponsorDailyUsagePersistencePort sponsorDailyUsagePersistencePort;
  @Mock private QuestionRewardIntentPersistencePort questionRewardIntentPersistencePort;
  @Mock private UpdateTransactionPort updateTransactionPort;
  @Mock private RecordTransactionAuditPort recordTransactionAuditPort;
  @Mock private Eip7702ChainPort eip7702ChainPort;
  @Mock private LoadTreasuryKeyPort loadTreasuryKeyPort;
  @Mock private ReserveNoncePort reserveNoncePort;
  @Mock private Web3ContractPort web3ContractPort;
  @Mock private LoadTransferRuntimeConfigPort loadTransferRuntimeConfigPort;
  @Mock private Eip7702AuthorizationPort eip7702AuthorizationPort;
  @Mock private Eip7702TransactionCodecPort eip7702TransactionCodecPort;
  @Mock private VerifyExecutionSignaturePort executionSignatureVerifier;

  private SubmitTokenTransferService service;

  @BeforeEach
  void setUp() {
    service =
        new SubmitTokenTransferService(
            transferPreparePersistencePort,
            transferTransactionPersistencePort,
            sponsorDailyUsagePersistencePort,
            questionRewardIntentPersistencePort,
            updateTransactionPort,
            recordTransactionAuditPort,
            eip7702ChainPort,
            loadTreasuryKeyPort,
            reserveNoncePort,
            web3ContractPort,
            loadTransferRuntimeConfigPort,
            eip7702AuthorizationPort,
            eip7702TransactionCodecPort,
            executionSignatureVerifier);
  }

  @Test
  void execute_throws_whenCommandNull() {
    assertThatThrownBy(() -> service.execute(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("command is required");
  }

  @Test
  void execute_throws_whenPrepareOwnerMismatch() {
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(99L, "prepare-1", "0xabc", "0xdef");
    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findForUpdateByPrepareId("prepare-1"))
        .thenReturn(Optional.of(submittedPrepare(7L, 10L)));

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("prepare owner mismatch");
  }

  @Test
  void execute_throws_whenPrepareNotFound() {
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(99L, "missing-prepare", "0xabc", "0xdef");
    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findForUpdateByPrepareId("missing-prepare"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("prepareId not found");
  }

  @Test
  void execute_returnsExistingSubmittedTransaction_whenAlreadySubmitted() {
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(7L, "prepare-1", "0xabc", "0xdef");
    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findForUpdateByPrepareId("prepare-1"))
        .thenReturn(Optional.of(submittedPrepare(7L, 10L)));
    when(transferTransactionPersistencePort.findById(10L))
        .thenReturn(
            Optional.of(
                TransferTransaction.builder()
                    .id(10L)
                    .idempotencyKey("domain:QUESTION_REWARD:101:7")
                    .referenceType(Web3ReferenceType.USER_TO_USER)
                    .referenceId("101")
                    .fromUserId(7L)
                    .toUserId(22L)
                    .fromAddress("0x" + "a".repeat(40))
                    .toAddress("0x" + "b".repeat(40))
                    .amountWei(BigInteger.TEN)
                    .txType(Web3TxType.EIP7702)
                    .status(Web3TxStatus.PENDING)
                    .txHash("0x" + "c".repeat(64))
                    .build()));

    SubmitTokenTransferResult result = service.execute(command);

    assertThat(result.transactionId()).isEqualTo(10L);
    assertThat(result.status()).isEqualTo(Web3TxStatus.PENDING.name());
    assertThat(result.txHash()).isEqualTo("0x" + "c".repeat(64));
  }

  @Test
  void execute_throws_whenSubmittedTransactionMissing() {
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(7L, "prepare-1", "0xabc", "0xdef");
    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findForUpdateByPrepareId("prepare-1"))
        .thenReturn(Optional.of(submittedPrepare(7L, 10L)));
    when(transferTransactionPersistencePort.findById(10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("submitted transaction not found");
  }

  @Test
  void execute_throws_whenQuestionRewardReferenceIdInvalid() {
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(7L, "prepare-1", "0xabc", "0xdef");
    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findForUpdateByPrepareId("prepare-1"))
        .thenReturn(
            Optional.of(
                activePrepare(
                    "domain:QUESTION_REWARD:not-number:7",
                    TokenTransferReferenceType.USER_TO_USER,
                    "not-number")));

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("invalid question reward referenceId in prepare");
  }

  @Test
  void execute_throws_whenPrepareExpired() {
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(7L, "prepare-1", "0xabc", "0xdef");
    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findForUpdateByPrepareId("prepare-1"))
        .thenReturn(
            Optional.of(
                activePrepare(
                        "domain:LEVEL_UP_REWARD:101:7",
                        TokenTransferReferenceType.SERVER_TO_USER,
                        "101")
                    .toBuilder()
                    .authExpiresAt(LocalDateTime.now().minusMinutes(1))
                    .build()));

    assertThatThrownBy(() -> service.execute(command)).isInstanceOf(Web3TransferException.class);
  }

  @Test
  void execute_returnsExistingByIdempotency_whenDuplicateIntent() {
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(7L, "prepare-1", "0xabc", "0xdef");
    TransferPrepare prepare =
        activePrepare(
            "domain:LEVEL_UP_REWARD:101:7", TokenTransferReferenceType.SERVER_TO_USER, "101");
    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findForUpdateByPrepareId("prepare-1"))
        .thenReturn(Optional.of(prepare));
    when(transferTransactionPersistencePort.findByIdempotencyKey(prepare.getIdempotencyKey()))
        .thenReturn(
            Optional.of(
                TransferTransaction.builder()
                    .id(33L)
                    .idempotencyKey(prepare.getIdempotencyKey())
                    .referenceType(Web3ReferenceType.LEVEL_UP_REWARD)
                    .referenceId("101")
                    .fromUserId(7L)
                    .toUserId(22L)
                    .fromAddress("0x" + "a".repeat(40))
                    .toAddress("0x" + "b".repeat(40))
                    .amountWei(BigInteger.TEN)
                    .txType(Web3TxType.EIP7702)
                    .status(Web3TxStatus.SIGNED)
                    .txHash("0x" + "c".repeat(64))
                    .build()));

    SubmitTokenTransferResult result = service.execute(command);

    assertThat(result.transactionId()).isEqualTo(33L);
    verify(transferPreparePersistencePort).update(any(TransferPrepare.class));
  }

  @Test
  void execute_success_broadcastsAndReturnsPending() {
    String authorizationSignature = "0x" + "b".repeat(130);
    String executionSignature = "0x" + "c".repeat(130);
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(7L, "prepare-1", authorizationSignature, executionSignature);
    TransferPrepare prepare =
        activePrepare(
            "domain:LEVEL_UP_REWARD:101:7", TokenTransferReferenceType.SERVER_TO_USER, "101");

    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findForUpdateByPrepareId("prepare-1"))
        .thenReturn(Optional.of(prepare));
    when(transferTransactionPersistencePort.findByIdempotencyKey(prepare.getIdempotencyKey()))
        .thenReturn(Optional.empty());
    when(eip7702ChainPort.loadPendingAccountNonce(prepare.getAuthorityAddress()))
        .thenReturn(BigInteger.valueOf(prepare.getAuthorityNonce()));
    when(loadTreasuryKeyPort.loadByAlias("sponsor", "kek"))
        .thenReturn(
            Optional.of(
                LoadTreasuryKeyPort.TreasuryKeyMaterial.of("0x" + "f".repeat(40), "f".repeat(64))));

    Eip7702ChainPort.AuthorizationTuple authTuple =
        new Eip7702ChainPort.AuthorizationTuple(
            BigInteger.valueOf(11155111L),
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            BigInteger.ZERO,
            BigInteger.ONE,
            BigInteger.ONE);
    when(eip7702AuthorizationPort.toAuthorizationTuple(
            11155111L,
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            authorizationSignature))
        .thenReturn(authTuple);
    when(eip7702TransactionCodecPort.encodeTransferData(
            prepare.getToAddress(), prepare.getAmountWei()))
        .thenReturn("0x");
    when(eip7702TransactionCodecPort.hashCalls(any())).thenReturn("0x" + "a".repeat(64));
    when(eip7702TransactionCodecPort.encodeExecute(any(), any())).thenReturn("0x");
    when(eip7702ChainPort.estimateGasWithAuthorization(
            "0x" + "f".repeat(40),
            prepare.getAuthorityAddress(),
            "0x",
            java.util.List.of(authTuple)))
        .thenReturn(BigInteger.valueOf(100_000));
    when(eip7702ChainPort.loadSponsorFeePlan())
        .thenReturn(new Eip7702ChainPort.FeePlan(BigInteger.ONE, BigInteger.valueOf(2)));
    when(sponsorDailyUsagePersistencePort.findForUpdate(anyLong(), any()))
        .thenReturn(Optional.empty());
    when(sponsorDailyUsagePersistencePort.create(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(sponsorDailyUsagePersistencePort.update(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(eip7702AuthorizationPort.verifySigner(
            11155111L,
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            authorizationSignature,
            prepare.getAuthorityAddress()))
        .thenReturn(true);
    when(executionSignatureVerifier.verify(
            prepare.getAuthorityAddress(),
            prepare.getPrepareId(),
            "0x" + "a".repeat(64),
            BigInteger.valueOf(prepare.getAuthExpiresAt().toEpochSecond(java.time.ZoneOffset.UTC)),
            executionSignature))
        .thenReturn(true);
    when(reserveNoncePort.reserveNextNonce("0x" + "f".repeat(40))).thenReturn(99L);
    when(eip7702TransactionCodecPort.signAndEncode(any()))
        .thenReturn(new Eip7702TransactionCodecPort.SignedPayload("0xraw", "0x" + "1".repeat(64)));
    when(transferTransactionPersistencePort.createAndFlush(any()))
        .thenReturn(
            TransferTransaction.builder()
                .id(55L)
                .idempotencyKey(prepare.getIdempotencyKey())
                .referenceType(Web3ReferenceType.LEVEL_UP_REWARD)
                .referenceId("101")
                .fromUserId(7L)
                .toUserId(22L)
                .fromAddress("0x" + "f".repeat(40))
                .toAddress(prepare.getAuthorityAddress())
                .amountWei(prepare.getAmountWei())
                .txType(Web3TxType.EIP7702)
                .status(Web3TxStatus.CREATED)
                .build());
    when(web3ContractPort.broadcast(any()))
        .thenReturn(new Web3ContractPort.BroadcastResult(true, null, null, "rpc-main"));

    SubmitTokenTransferResult result = service.execute(command);

    assertThat(result.transactionId()).isEqualTo(55L);
    assertThat(result.status()).isEqualTo(Web3TxStatus.PENDING.name());
    assertThat(result.txHash()).isEqualTo("0x" + "1".repeat(64));
  }

  @Test
  void execute_broadcastFailure_returnsSignedStatus() {
    String authorizationSignature = "0x" + "b".repeat(130);
    String executionSignature = "0x" + "c".repeat(130);
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(7L, "prepare-1", authorizationSignature, executionSignature);
    TransferPrepare prepare =
        activePrepare(
            "domain:LEVEL_UP_REWARD:101:7", TokenTransferReferenceType.SERVER_TO_USER, "101");

    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findForUpdateByPrepareId("prepare-1"))
        .thenReturn(Optional.of(prepare));
    when(transferTransactionPersistencePort.findByIdempotencyKey(prepare.getIdempotencyKey()))
        .thenReturn(Optional.empty());
    when(eip7702ChainPort.loadPendingAccountNonce(prepare.getAuthorityAddress()))
        .thenReturn(BigInteger.valueOf(prepare.getAuthorityNonce()));
    when(loadTreasuryKeyPort.loadByAlias("sponsor", "kek"))
        .thenReturn(
            Optional.of(
                LoadTreasuryKeyPort.TreasuryKeyMaterial.of("0x" + "f".repeat(40), "f".repeat(64))));
    Eip7702ChainPort.AuthorizationTuple authTuple =
        new Eip7702ChainPort.AuthorizationTuple(
            BigInteger.valueOf(11155111L),
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            BigInteger.ZERO,
            BigInteger.ONE,
            BigInteger.ONE);
    when(eip7702AuthorizationPort.toAuthorizationTuple(
            11155111L,
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            authorizationSignature))
        .thenReturn(authTuple);
    when(eip7702TransactionCodecPort.encodeTransferData(
            prepare.getToAddress(), prepare.getAmountWei()))
        .thenReturn("0x");
    when(eip7702TransactionCodecPort.hashCalls(any())).thenReturn("0x" + "a".repeat(64));
    when(eip7702TransactionCodecPort.encodeExecute(any(), any())).thenReturn("0x");
    when(eip7702ChainPort.estimateGasWithAuthorization(
            "0x" + "f".repeat(40),
            prepare.getAuthorityAddress(),
            "0x",
            java.util.List.of(authTuple)))
        .thenReturn(BigInteger.valueOf(100_000));
    when(eip7702ChainPort.loadSponsorFeePlan())
        .thenReturn(new Eip7702ChainPort.FeePlan(BigInteger.ONE, BigInteger.valueOf(2)));
    when(sponsorDailyUsagePersistencePort.findForUpdate(anyLong(), any()))
        .thenReturn(Optional.empty());
    when(eip7702AuthorizationPort.verifySigner(
            11155111L,
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            authorizationSignature,
            prepare.getAuthorityAddress()))
        .thenReturn(true);
    when(executionSignatureVerifier.verify(
            prepare.getAuthorityAddress(),
            prepare.getPrepareId(),
            "0x" + "a".repeat(64),
            BigInteger.valueOf(prepare.getAuthExpiresAt().toEpochSecond(java.time.ZoneOffset.UTC)),
            executionSignature))
        .thenReturn(true);
    when(reserveNoncePort.reserveNextNonce("0x" + "f".repeat(40))).thenReturn(99L);
    when(eip7702TransactionCodecPort.signAndEncode(any()))
        .thenReturn(new Eip7702TransactionCodecPort.SignedPayload("0xraw", "0x" + "1".repeat(64)));
    when(transferTransactionPersistencePort.createAndFlush(any()))
        .thenReturn(
            TransferTransaction.builder()
                .id(77L)
                .idempotencyKey(prepare.getIdempotencyKey())
                .referenceType(Web3ReferenceType.LEVEL_UP_REWARD)
                .referenceId("101")
                .fromUserId(7L)
                .toUserId(22L)
                .fromAddress("0x" + "f".repeat(40))
                .toAddress(prepare.getAuthorityAddress())
                .amountWei(prepare.getAmountWei())
                .txType(Web3TxType.EIP7702)
                .status(Web3TxStatus.CREATED)
                .build());
    when(web3ContractPort.broadcast(any()))
        .thenReturn(
            new Web3ContractPort.BroadcastResult(false, null, "BROADCAST_FAILED", "rpc-main"));

    SubmitTokenTransferResult result = service.execute(command);

    assertThat(result.transactionId()).isEqualTo(77L);
    assertThat(result.status()).isEqualTo(Web3TxStatus.SIGNED.name());
    assertThat(result.txHash()).isEqualTo("0x" + "1".repeat(64));
  }

  @Test
  void execute_throws_whenEstimatedGasNonPositive() {
    String authorizationSignature = "0x" + "b".repeat(130);
    String executionSignature = "0x" + "c".repeat(130);
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(7L, "prepare-1", authorizationSignature, executionSignature);
    TransferPrepare prepare =
        activePrepare(
            "domain:LEVEL_UP_REWARD:101:7", TokenTransferReferenceType.SERVER_TO_USER, "101");
    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findForUpdateByPrepareId("prepare-1"))
        .thenReturn(Optional.of(prepare));
    when(transferTransactionPersistencePort.findByIdempotencyKey(prepare.getIdempotencyKey()))
        .thenReturn(Optional.empty());
    when(eip7702ChainPort.loadPendingAccountNonce(prepare.getAuthorityAddress()))
        .thenReturn(BigInteger.valueOf(prepare.getAuthorityNonce()));
    when(loadTreasuryKeyPort.loadByAlias("sponsor", "kek"))
        .thenReturn(
            Optional.of(
                LoadTreasuryKeyPort.TreasuryKeyMaterial.of("0x" + "f".repeat(40), "f".repeat(64))));
    Eip7702ChainPort.AuthorizationTuple authTuple =
        new Eip7702ChainPort.AuthorizationTuple(
            BigInteger.valueOf(11155111L),
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            BigInteger.ZERO,
            BigInteger.ONE,
            BigInteger.ONE);
    when(eip7702AuthorizationPort.toAuthorizationTuple(
            11155111L,
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            authorizationSignature))
        .thenReturn(authTuple);
    when(eip7702TransactionCodecPort.encodeTransferData(
            prepare.getToAddress(), prepare.getAmountWei()))
        .thenReturn("0x");
    when(eip7702TransactionCodecPort.hashCalls(org.mockito.ArgumentMatchers.anyList()))
        .thenReturn("0x" + "a".repeat(64));
    when(eip7702TransactionCodecPort.encodeExecute(
            org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any()))
        .thenReturn("0x");
    when(eip7702ChainPort.estimateGasWithAuthorization(
            "0x" + "f".repeat(40),
            prepare.getAuthorityAddress(),
            "0x",
            java.util.List.of(authTuple)))
        .thenReturn(BigInteger.ZERO);

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("estimatedGas must be > 0");
  }

  @Test
  void execute_throws_whenSponsorSignerKeyMissing() {
    String authorizationSignature = "0x" + "b".repeat(130);
    String executionSignature = "0x" + "c".repeat(130);
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(7L, "prepare-1", authorizationSignature, executionSignature);
    TransferPrepare prepare =
        activePrepare(
            "domain:LEVEL_UP_REWARD:101:7", TokenTransferReferenceType.SERVER_TO_USER, "101");
    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findForUpdateByPrepareId("prepare-1"))
        .thenReturn(Optional.of(prepare));
    when(transferTransactionPersistencePort.findByIdempotencyKey(prepare.getIdempotencyKey()))
        .thenReturn(Optional.empty());
    when(eip7702ChainPort.loadPendingAccountNonce(prepare.getAuthorityAddress()))
        .thenReturn(BigInteger.valueOf(prepare.getAuthorityNonce()));
    when(loadTreasuryKeyPort.loadByAlias("sponsor", "kek")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("sponsor signer key is missing");
  }

  @Test
  void execute_throws_whenEstimatedGasNull() {
    String authorizationSignature = "0x" + "b".repeat(130);
    String executionSignature = "0x" + "c".repeat(130);
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(7L, "prepare-1", authorizationSignature, executionSignature);
    TransferPrepare prepare =
        activePrepare(
            "domain:LEVEL_UP_REWARD:101:7", TokenTransferReferenceType.SERVER_TO_USER, "101");
    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findForUpdateByPrepareId("prepare-1"))
        .thenReturn(Optional.of(prepare));
    when(transferTransactionPersistencePort.findByIdempotencyKey(prepare.getIdempotencyKey()))
        .thenReturn(Optional.empty());
    when(eip7702ChainPort.loadPendingAccountNonce(prepare.getAuthorityAddress()))
        .thenReturn(BigInteger.valueOf(prepare.getAuthorityNonce()));
    when(loadTreasuryKeyPort.loadByAlias("sponsor", "kek"))
        .thenReturn(
            Optional.of(
                LoadTreasuryKeyPort.TreasuryKeyMaterial.of("0x" + "f".repeat(40), "f".repeat(64))));
    Eip7702ChainPort.AuthorizationTuple authTuple =
        new Eip7702ChainPort.AuthorizationTuple(
            BigInteger.valueOf(11155111L),
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            BigInteger.ZERO,
            BigInteger.ONE,
            BigInteger.ONE);
    when(eip7702AuthorizationPort.toAuthorizationTuple(
            11155111L,
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            authorizationSignature))
        .thenReturn(authTuple);
    when(eip7702TransactionCodecPort.encodeTransferData(
            prepare.getToAddress(), prepare.getAmountWei()))
        .thenReturn("0x");
    when(eip7702TransactionCodecPort.hashCalls(any())).thenReturn("0x" + "a".repeat(64));
    when(eip7702TransactionCodecPort.encodeExecute(any(), any())).thenReturn("0x");
    when(eip7702ChainPort.estimateGasWithAuthorization(
            "0x" + "f".repeat(40),
            prepare.getAuthorityAddress(),
            "0x",
            java.util.List.of(authTuple)))
        .thenReturn(null);

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("estimatedGas must be > 0");
  }

  @Test
  void execute_throws_whenEstimatedGasExceedsMaxGasLimit() {
    String authorizationSignature = "0x" + "b".repeat(130);
    String executionSignature = "0x" + "c".repeat(130);
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(7L, "prepare-1", authorizationSignature, executionSignature);
    TransferPrepare prepare =
        activePrepare(
            "domain:LEVEL_UP_REWARD:101:7", TokenTransferReferenceType.SERVER_TO_USER, "101");
    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findForUpdateByPrepareId("prepare-1"))
        .thenReturn(Optional.of(prepare));
    when(transferTransactionPersistencePort.findByIdempotencyKey(prepare.getIdempotencyKey()))
        .thenReturn(Optional.empty());
    when(eip7702ChainPort.loadPendingAccountNonce(prepare.getAuthorityAddress()))
        .thenReturn(BigInteger.valueOf(prepare.getAuthorityNonce()));
    when(loadTreasuryKeyPort.loadByAlias("sponsor", "kek"))
        .thenReturn(
            Optional.of(
                LoadTreasuryKeyPort.TreasuryKeyMaterial.of("0x" + "f".repeat(40), "f".repeat(64))));
    Eip7702ChainPort.AuthorizationTuple authTuple =
        new Eip7702ChainPort.AuthorizationTuple(
            BigInteger.valueOf(11155111L),
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            BigInteger.ZERO,
            BigInteger.ONE,
            BigInteger.ONE);
    when(eip7702AuthorizationPort.toAuthorizationTuple(
            11155111L,
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            authorizationSignature))
        .thenReturn(authTuple);
    when(eip7702TransactionCodecPort.encodeTransferData(
            prepare.getToAddress(), prepare.getAmountWei()))
        .thenReturn("0x");
    when(eip7702TransactionCodecPort.hashCalls(any())).thenReturn("0x" + "a".repeat(64));
    when(eip7702TransactionCodecPort.encodeExecute(any(), any())).thenReturn("0x");
    when(eip7702ChainPort.estimateGasWithAuthorization(
            "0x" + "f".repeat(40),
            prepare.getAuthorityAddress(),
            "0x",
            java.util.List.of(authTuple)))
        .thenReturn(BigInteger.valueOf(2_000_000L));

    assertThatThrownBy(() -> service.execute(command)).isInstanceOf(Web3TransferException.class);
  }

  @Test
  void execute_success_usesBroadcastTxHash_whenProvided() {
    String authorizationSignature = "0x" + "b".repeat(130);
    String executionSignature = "0x" + "c".repeat(130);
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(7L, "prepare-1", authorizationSignature, executionSignature);
    TransferPrepare prepare =
        activePrepare(
            "domain:LEVEL_UP_REWARD:101:7", TokenTransferReferenceType.SERVER_TO_USER, "101");

    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findForUpdateByPrepareId("prepare-1"))
        .thenReturn(Optional.of(prepare));
    when(transferTransactionPersistencePort.findByIdempotencyKey(prepare.getIdempotencyKey()))
        .thenReturn(Optional.empty());
    when(eip7702ChainPort.loadPendingAccountNonce(prepare.getAuthorityAddress()))
        .thenReturn(BigInteger.valueOf(prepare.getAuthorityNonce()));
    when(loadTreasuryKeyPort.loadByAlias("sponsor", "kek"))
        .thenReturn(
            Optional.of(
                LoadTreasuryKeyPort.TreasuryKeyMaterial.of("0x" + "f".repeat(40), "f".repeat(64))));

    Eip7702ChainPort.AuthorizationTuple authTuple =
        new Eip7702ChainPort.AuthorizationTuple(
            BigInteger.valueOf(11155111L),
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            BigInteger.ZERO,
            BigInteger.ONE,
            BigInteger.ONE);
    when(eip7702AuthorizationPort.toAuthorizationTuple(
            11155111L,
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            authorizationSignature))
        .thenReturn(authTuple);
    when(eip7702TransactionCodecPort.encodeTransferData(
            prepare.getToAddress(), prepare.getAmountWei()))
        .thenReturn("0x");
    when(eip7702TransactionCodecPort.hashCalls(any())).thenReturn("0x" + "a".repeat(64));
    when(eip7702TransactionCodecPort.encodeExecute(any(), any())).thenReturn("0x");
    when(eip7702ChainPort.estimateGasWithAuthorization(
            "0x" + "f".repeat(40),
            prepare.getAuthorityAddress(),
            "0x",
            java.util.List.of(authTuple)))
        .thenReturn(BigInteger.valueOf(100_000));
    when(eip7702ChainPort.loadSponsorFeePlan())
        .thenReturn(new Eip7702ChainPort.FeePlan(BigInteger.ONE, BigInteger.valueOf(2)));
    when(sponsorDailyUsagePersistencePort.findForUpdate(anyLong(), any()))
        .thenReturn(Optional.empty());
    when(sponsorDailyUsagePersistencePort.create(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(sponsorDailyUsagePersistencePort.update(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(eip7702AuthorizationPort.verifySigner(
            11155111L,
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            authorizationSignature,
            prepare.getAuthorityAddress()))
        .thenReturn(true);
    when(executionSignatureVerifier.verify(
            prepare.getAuthorityAddress(),
            prepare.getPrepareId(),
            "0x" + "a".repeat(64),
            BigInteger.valueOf(prepare.getAuthExpiresAt().toEpochSecond(java.time.ZoneOffset.UTC)),
            executionSignature))
        .thenReturn(true);
    when(reserveNoncePort.reserveNextNonce("0x" + "f".repeat(40))).thenReturn(99L);
    when(eip7702TransactionCodecPort.signAndEncode(any()))
        .thenReturn(new Eip7702TransactionCodecPort.SignedPayload("0xraw", "0x" + "1".repeat(64)));
    when(transferTransactionPersistencePort.createAndFlush(any()))
        .thenReturn(
            TransferTransaction.builder()
                .id(88L)
                .idempotencyKey(prepare.getIdempotencyKey())
                .referenceType(Web3ReferenceType.LEVEL_UP_REWARD)
                .referenceId("101")
                .fromUserId(7L)
                .toUserId(22L)
                .fromAddress("0x" + "f".repeat(40))
                .toAddress(prepare.getAuthorityAddress())
                .amountWei(prepare.getAmountWei())
                .txType(Web3TxType.EIP7702)
                .status(Web3TxStatus.CREATED)
                .build());
    String broadcastHash = "0x" + "9".repeat(64);
    when(web3ContractPort.broadcast(any()))
        .thenReturn(new Web3ContractPort.BroadcastResult(true, broadcastHash, null, "rpc-main"));

    SubmitTokenTransferResult result = service.execute(command);

    assertThat(result.status()).isEqualTo(Web3TxStatus.PENDING.name());
    assertThat(result.txHash()).isEqualTo(broadcastHash);
    verify(updateTransactionPort).markPending(88L, broadcastHash);
  }

  @Test
  void execute_success_usesSignedPayloadTxHash_whenBroadcastHashBlank() {
    String authorizationSignature = "0x" + "b".repeat(130);
    String executionSignature = "0x" + "c".repeat(130);
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(7L, "prepare-1", authorizationSignature, executionSignature);
    TransferPrepare prepare =
        activePrepare(
            "domain:LEVEL_UP_REWARD:101:7", TokenTransferReferenceType.SERVER_TO_USER, "101");

    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findForUpdateByPrepareId("prepare-1"))
        .thenReturn(Optional.of(prepare));
    when(transferTransactionPersistencePort.findByIdempotencyKey(prepare.getIdempotencyKey()))
        .thenReturn(Optional.empty());
    when(eip7702ChainPort.loadPendingAccountNonce(prepare.getAuthorityAddress()))
        .thenReturn(BigInteger.valueOf(prepare.getAuthorityNonce()));
    when(loadTreasuryKeyPort.loadByAlias("sponsor", "kek"))
        .thenReturn(
            Optional.of(
                LoadTreasuryKeyPort.TreasuryKeyMaterial.of("0x" + "f".repeat(40), "f".repeat(64))));
    Eip7702ChainPort.AuthorizationTuple authTuple =
        new Eip7702ChainPort.AuthorizationTuple(
            BigInteger.valueOf(11155111L),
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            BigInteger.ZERO,
            BigInteger.ONE,
            BigInteger.ONE);
    when(eip7702AuthorizationPort.toAuthorizationTuple(
            11155111L,
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            authorizationSignature))
        .thenReturn(authTuple);
    when(eip7702TransactionCodecPort.encodeTransferData(
            prepare.getToAddress(), prepare.getAmountWei()))
        .thenReturn("0x");
    when(eip7702TransactionCodecPort.hashCalls(any())).thenReturn("0x" + "a".repeat(64));
    when(eip7702TransactionCodecPort.encodeExecute(any(), any())).thenReturn("0x");
    when(eip7702ChainPort.estimateGasWithAuthorization(
            "0x" + "f".repeat(40),
            prepare.getAuthorityAddress(),
            "0x",
            java.util.List.of(authTuple)))
        .thenReturn(BigInteger.valueOf(100_000));
    when(eip7702ChainPort.loadSponsorFeePlan())
        .thenReturn(new Eip7702ChainPort.FeePlan(BigInteger.ONE, BigInteger.valueOf(2)));
    when(sponsorDailyUsagePersistencePort.findForUpdate(anyLong(), any()))
        .thenReturn(Optional.empty());
    when(sponsorDailyUsagePersistencePort.create(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(sponsorDailyUsagePersistencePort.update(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(eip7702AuthorizationPort.verifySigner(
            11155111L,
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            authorizationSignature,
            prepare.getAuthorityAddress()))
        .thenReturn(true);
    when(executionSignatureVerifier.verify(
            prepare.getAuthorityAddress(),
            prepare.getPrepareId(),
            "0x" + "a".repeat(64),
            BigInteger.valueOf(prepare.getAuthExpiresAt().toEpochSecond(java.time.ZoneOffset.UTC)),
            executionSignature))
        .thenReturn(true);
    when(reserveNoncePort.reserveNextNonce("0x" + "f".repeat(40))).thenReturn(99L);
    String signedHash = "0x" + "1".repeat(64);
    when(eip7702TransactionCodecPort.signAndEncode(any()))
        .thenReturn(new Eip7702TransactionCodecPort.SignedPayload("0xraw", signedHash));
    when(transferTransactionPersistencePort.createAndFlush(any()))
        .thenReturn(
            TransferTransaction.builder()
                .id(90L)
                .idempotencyKey(prepare.getIdempotencyKey())
                .referenceType(Web3ReferenceType.LEVEL_UP_REWARD)
                .referenceId("101")
                .fromUserId(7L)
                .toUserId(22L)
                .fromAddress("0x" + "f".repeat(40))
                .toAddress(prepare.getAuthorityAddress())
                .amountWei(prepare.getAmountWei())
                .txType(Web3TxType.EIP7702)
                .status(Web3TxStatus.CREATED)
                .build());
    when(web3ContractPort.broadcast(any()))
        .thenReturn(new Web3ContractPort.BroadcastResult(true, "   ", null, "rpc-main"));

    SubmitTokenTransferResult result = service.execute(command);

    assertThat(result.status()).isEqualTo(Web3TxStatus.PENDING.name());
    assertThat(result.txHash()).isEqualTo(signedHash);
    verify(updateTransactionPort).markPending(90L, signedHash);
  }

  @Test
  void execute_broadcastFailure_usesDefaultReason_whenFailureReasonMissing() {
    String authorizationSignature = "0x" + "b".repeat(130);
    String executionSignature = "0x" + "c".repeat(130);
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(7L, "prepare-1", authorizationSignature, executionSignature);
    TransferPrepare prepare =
        activePrepare(
            "domain:LEVEL_UP_REWARD:101:7", TokenTransferReferenceType.SERVER_TO_USER, "101");

    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findForUpdateByPrepareId("prepare-1"))
        .thenReturn(Optional.of(prepare));
    when(transferTransactionPersistencePort.findByIdempotencyKey(prepare.getIdempotencyKey()))
        .thenReturn(Optional.empty());
    when(eip7702ChainPort.loadPendingAccountNonce(prepare.getAuthorityAddress()))
        .thenReturn(BigInteger.valueOf(prepare.getAuthorityNonce()));
    when(loadTreasuryKeyPort.loadByAlias("sponsor", "kek"))
        .thenReturn(
            Optional.of(
                LoadTreasuryKeyPort.TreasuryKeyMaterial.of("0x" + "f".repeat(40), "f".repeat(64))));
    Eip7702ChainPort.AuthorizationTuple authTuple =
        new Eip7702ChainPort.AuthorizationTuple(
            BigInteger.valueOf(11155111L),
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            BigInteger.ZERO,
            BigInteger.ONE,
            BigInteger.ONE);
    when(eip7702AuthorizationPort.toAuthorizationTuple(
            11155111L,
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            authorizationSignature))
        .thenReturn(authTuple);
    when(eip7702TransactionCodecPort.encodeTransferData(
            prepare.getToAddress(), prepare.getAmountWei()))
        .thenReturn("0x");
    when(eip7702TransactionCodecPort.hashCalls(any())).thenReturn("0x" + "a".repeat(64));
    when(eip7702TransactionCodecPort.encodeExecute(any(), any())).thenReturn("0x");
    when(eip7702ChainPort.estimateGasWithAuthorization(
            "0x" + "f".repeat(40),
            prepare.getAuthorityAddress(),
            "0x",
            java.util.List.of(authTuple)))
        .thenReturn(BigInteger.valueOf(100_000));
    when(eip7702ChainPort.loadSponsorFeePlan())
        .thenReturn(new Eip7702ChainPort.FeePlan(BigInteger.ONE, BigInteger.valueOf(2)));
    when(sponsorDailyUsagePersistencePort.findForUpdate(anyLong(), any()))
        .thenReturn(Optional.empty());
    when(eip7702AuthorizationPort.verifySigner(
            11155111L,
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            authorizationSignature,
            prepare.getAuthorityAddress()))
        .thenReturn(true);
    when(executionSignatureVerifier.verify(
            prepare.getAuthorityAddress(),
            prepare.getPrepareId(),
            "0x" + "a".repeat(64),
            BigInteger.valueOf(prepare.getAuthExpiresAt().toEpochSecond(java.time.ZoneOffset.UTC)),
            executionSignature))
        .thenReturn(true);
    when(reserveNoncePort.reserveNextNonce("0x" + "f".repeat(40))).thenReturn(99L);
    when(eip7702TransactionCodecPort.signAndEncode(any()))
        .thenReturn(new Eip7702TransactionCodecPort.SignedPayload("0xraw", "0x" + "1".repeat(64)));
    when(transferTransactionPersistencePort.createAndFlush(any()))
        .thenReturn(
            TransferTransaction.builder()
                .id(89L)
                .idempotencyKey(prepare.getIdempotencyKey())
                .referenceType(Web3ReferenceType.LEVEL_UP_REWARD)
                .referenceId("101")
                .fromUserId(7L)
                .toUserId(22L)
                .fromAddress("0x" + "f".repeat(40))
                .toAddress(prepare.getAuthorityAddress())
                .amountWei(prepare.getAmountWei())
                .txType(Web3TxType.EIP7702)
                .status(Web3TxStatus.CREATED)
                .build());
    when(web3ContractPort.broadcast(any()))
        .thenReturn(new Web3ContractPort.BroadcastResult(false, null, null, "rpc-main"));

    SubmitTokenTransferResult result = service.execute(command);

    assertThat(result.status()).isEqualTo(Web3TxStatus.SIGNED.name());
    verify(updateTransactionPort)
        .scheduleRetry(
            eq(89L),
            eq(
                momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason
                    .BROADCAST_FAILED
                    .code()),
            any(LocalDateTime.class));
    verify(updateTransactionPort, never()).markPending(anyLong(), anyString());
  }

  @Test
  void execute_broadcastFailure_usesDefaultReason_whenFailureReasonBlank() {
    String authorizationSignature = "0x" + "b".repeat(130);
    String executionSignature = "0x" + "c".repeat(130);
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(7L, "prepare-1", authorizationSignature, executionSignature);
    TransferPrepare prepare =
        activePrepare(
            "domain:LEVEL_UP_REWARD:101:7", TokenTransferReferenceType.SERVER_TO_USER, "101");

    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findForUpdateByPrepareId("prepare-1"))
        .thenReturn(Optional.of(prepare));
    when(transferTransactionPersistencePort.findByIdempotencyKey(prepare.getIdempotencyKey()))
        .thenReturn(Optional.empty());
    when(eip7702ChainPort.loadPendingAccountNonce(prepare.getAuthorityAddress()))
        .thenReturn(BigInteger.valueOf(prepare.getAuthorityNonce()));
    when(loadTreasuryKeyPort.loadByAlias("sponsor", "kek"))
        .thenReturn(
            Optional.of(
                LoadTreasuryKeyPort.TreasuryKeyMaterial.of("0x" + "f".repeat(40), "f".repeat(64))));
    Eip7702ChainPort.AuthorizationTuple authTuple =
        new Eip7702ChainPort.AuthorizationTuple(
            BigInteger.valueOf(11155111L),
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            BigInteger.ZERO,
            BigInteger.ONE,
            BigInteger.ONE);
    when(eip7702AuthorizationPort.toAuthorizationTuple(
            11155111L,
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            authorizationSignature))
        .thenReturn(authTuple);
    when(eip7702TransactionCodecPort.encodeTransferData(
            prepare.getToAddress(), prepare.getAmountWei()))
        .thenReturn("0x");
    when(eip7702TransactionCodecPort.hashCalls(any())).thenReturn("0x" + "a".repeat(64));
    when(eip7702TransactionCodecPort.encodeExecute(any(), any())).thenReturn("0x");
    when(eip7702ChainPort.estimateGasWithAuthorization(
            "0x" + "f".repeat(40),
            prepare.getAuthorityAddress(),
            "0x",
            java.util.List.of(authTuple)))
        .thenReturn(BigInteger.valueOf(100_000));
    when(eip7702ChainPort.loadSponsorFeePlan())
        .thenReturn(new Eip7702ChainPort.FeePlan(BigInteger.ONE, BigInteger.valueOf(2)));
    when(sponsorDailyUsagePersistencePort.findForUpdate(anyLong(), any()))
        .thenReturn(Optional.empty());
    when(eip7702AuthorizationPort.verifySigner(
            11155111L,
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            authorizationSignature,
            prepare.getAuthorityAddress()))
        .thenReturn(true);
    when(executionSignatureVerifier.verify(
            prepare.getAuthorityAddress(),
            prepare.getPrepareId(),
            "0x" + "a".repeat(64),
            BigInteger.valueOf(prepare.getAuthExpiresAt().toEpochSecond(java.time.ZoneOffset.UTC)),
            executionSignature))
        .thenReturn(true);
    when(reserveNoncePort.reserveNextNonce("0x" + "f".repeat(40))).thenReturn(99L);
    when(eip7702TransactionCodecPort.signAndEncode(any()))
        .thenReturn(new Eip7702TransactionCodecPort.SignedPayload("0xraw", "0x" + "1".repeat(64)));
    when(transferTransactionPersistencePort.createAndFlush(any()))
        .thenReturn(
            TransferTransaction.builder()
                .id(91L)
                .idempotencyKey(prepare.getIdempotencyKey())
                .referenceType(Web3ReferenceType.LEVEL_UP_REWARD)
                .referenceId("101")
                .fromUserId(7L)
                .toUserId(22L)
                .fromAddress("0x" + "f".repeat(40))
                .toAddress(prepare.getAuthorityAddress())
                .amountWei(prepare.getAmountWei())
                .txType(Web3TxType.EIP7702)
                .status(Web3TxStatus.CREATED)
                .build());
    when(web3ContractPort.broadcast(any()))
        .thenReturn(new Web3ContractPort.BroadcastResult(false, null, "   ", "rpc-main"));

    SubmitTokenTransferResult result = service.execute(command);

    assertThat(result.status()).isEqualTo(Web3TxStatus.SIGNED.name());
    verify(updateTransactionPort)
        .scheduleRetry(
            eq(91L),
            eq(
                momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason
                    .BROADCAST_FAILED
                    .code()),
            any(LocalDateTime.class));
  }

  @Test
  void setKstClock_updatesField_andRejectsNull() {
    service.setKstClock(Clock.systemUTC());
    assertThatThrownBy(() -> service.setKstClock(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void execute_throws_whenAuthorizationSignatureInvalid() {
    String authorizationSignature = "0x" + "b".repeat(130);
    String executionSignature = "0x" + "c".repeat(130);
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(7L, "prepare-1", authorizationSignature, executionSignature);
    TransferPrepare prepare =
        activePrepare(
            "domain:LEVEL_UP_REWARD:101:7", TokenTransferReferenceType.SERVER_TO_USER, "101");
    stubPreSignatureFlow(
        prepare, authorizationSignature, BigInteger.valueOf(100_000), BigInteger.valueOf(2));
    when(eip7702AuthorizationPort.verifySigner(
            11155111L,
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            authorizationSignature,
            prepare.getAuthorityAddress()))
        .thenReturn(false);

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("authorizationSignature does not match authority");
  }

  @Test
  void execute_throws_whenExecutionSignatureInvalid() {
    String authorizationSignature = "0x" + "b".repeat(130);
    String executionSignature = "0x" + "c".repeat(130);
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(7L, "prepare-1", authorizationSignature, executionSignature);
    TransferPrepare prepare =
        activePrepare(
            "domain:LEVEL_UP_REWARD:101:7", TokenTransferReferenceType.SERVER_TO_USER, "101");
    stubPreSignatureFlow(
        prepare, authorizationSignature, BigInteger.valueOf(100_000), BigInteger.valueOf(2));
    when(eip7702AuthorizationPort.verifySigner(
            11155111L,
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            authorizationSignature,
            prepare.getAuthorityAddress()))
        .thenReturn(true);
    when(executionSignatureVerifier.verify(
            prepare.getAuthorityAddress(),
            prepare.getPrepareId(),
            "0x" + "a".repeat(64),
            BigInteger.valueOf(prepare.getAuthExpiresAt().toEpochSecond(java.time.ZoneOffset.UTC)),
            executionSignature))
        .thenReturn(false);

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("executionSignature does not match authority");
  }

  @Test
  void execute_throws_whenAuthorityNonceMismatch() {
    String authorizationSignature = "0x" + "b".repeat(130);
    String executionSignature = "0x" + "c".repeat(130);
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(7L, "prepare-1", authorizationSignature, executionSignature);
    TransferPrepare prepare =
        activePrepare(
            "domain:LEVEL_UP_REWARD:101:7", TokenTransferReferenceType.SERVER_TO_USER, "101");
    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findForUpdateByPrepareId("prepare-1"))
        .thenReturn(Optional.of(prepare));
    when(transferTransactionPersistencePort.findByIdempotencyKey(prepare.getIdempotencyKey()))
        .thenReturn(Optional.empty());
    when(eip7702ChainPort.loadPendingAccountNonce(prepare.getAuthorityAddress()))
        .thenReturn(BigInteger.valueOf(prepare.getAuthorityNonce() + 1));

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(Web3TransferException.class)
        .hasMessageContaining("authority nonce mismatch");
  }

  @Test
  void execute_throws_whenAuthorityNonceOverflow() {
    String authorizationSignature = "0x" + "b".repeat(130);
    String executionSignature = "0x" + "c".repeat(130);
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(7L, "prepare-1", authorizationSignature, executionSignature);
    TransferPrepare prepare =
        activePrepare(
            "domain:LEVEL_UP_REWARD:101:7", TokenTransferReferenceType.SERVER_TO_USER, "101");
    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findForUpdateByPrepareId("prepare-1"))
        .thenReturn(Optional.of(prepare));
    when(transferTransactionPersistencePort.findByIdempotencyKey(prepare.getIdempotencyKey()))
        .thenReturn(Optional.empty());
    when(eip7702ChainPort.loadPendingAccountNonce(prepare.getAuthorityAddress()))
        .thenReturn(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE));

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(Web3TransferException.class)
        .hasMessageContaining("authority nonce overflow");
  }

  @Test
  void execute_throws_whenQuestionRewardReferenceIdBlank() {
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(7L, "prepare-1", "0xabc", "0xdef");
    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findForUpdateByPrepareId("prepare-1"))
        .thenReturn(
            Optional.of(
                activePrepare(
                    "domain:QUESTION_REWARD:101:7", TokenTransferReferenceType.USER_TO_USER, " ")));

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("invalid question reward referenceId in prepare");
  }

  @Test
  void execute_success_updatesDailyUsage_whenUsageAlreadyExists() {
    String authorizationSignature = "0x" + "b".repeat(130);
    String executionSignature = "0x" + "c".repeat(130);
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(7L, "prepare-1", authorizationSignature, executionSignature);
    TransferPrepare prepare =
        activePrepare(
            "domain:LEVEL_UP_REWARD:101:7", TokenTransferReferenceType.SERVER_TO_USER, "101");

    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findForUpdateByPrepareId("prepare-1"))
        .thenReturn(Optional.of(prepare));
    when(transferTransactionPersistencePort.findByIdempotencyKey(prepare.getIdempotencyKey()))
        .thenReturn(Optional.empty());
    when(eip7702ChainPort.loadPendingAccountNonce(prepare.getAuthorityAddress()))
        .thenReturn(BigInteger.valueOf(prepare.getAuthorityNonce()));
    when(loadTreasuryKeyPort.loadByAlias("sponsor", "kek"))
        .thenReturn(
            Optional.of(
                LoadTreasuryKeyPort.TreasuryKeyMaterial.of("0x" + "f".repeat(40), "f".repeat(64))));

    Eip7702ChainPort.AuthorizationTuple authTuple =
        new Eip7702ChainPort.AuthorizationTuple(
            BigInteger.valueOf(11155111L),
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            BigInteger.ZERO,
            BigInteger.ONE,
            BigInteger.ONE);
    when(eip7702AuthorizationPort.toAuthorizationTuple(
            11155111L,
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            authorizationSignature))
        .thenReturn(authTuple);
    when(eip7702TransactionCodecPort.encodeTransferData(
            prepare.getToAddress(), prepare.getAmountWei()))
        .thenReturn("0x");
    when(eip7702TransactionCodecPort.hashCalls(any())).thenReturn("0x" + "a".repeat(64));
    when(eip7702TransactionCodecPort.encodeExecute(any(), any())).thenReturn("0x");
    when(eip7702ChainPort.estimateGasWithAuthorization(
            "0x" + "f".repeat(40),
            prepare.getAuthorityAddress(),
            "0x",
            java.util.List.of(authTuple)))
        .thenReturn(BigInteger.valueOf(100_000));
    when(eip7702ChainPort.loadSponsorFeePlan())
        .thenReturn(new Eip7702ChainPort.FeePlan(BigInteger.ONE, BigInteger.valueOf(2)));
    when(sponsorDailyUsagePersistencePort.findForUpdate(anyLong(), any()))
        .thenReturn(
            Optional.of(
                momzzangseven.mztkbe.modules.web3.transfer.domain.model.SponsorDailyUsage.builder()
                    .userId(7L)
                    .usageDateKst(java.time.LocalDate.now())
                    .estimatedCostWei(BigInteger.valueOf(100))
                    .build()));
    when(sponsorDailyUsagePersistencePort.update(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(eip7702AuthorizationPort.verifySigner(
            11155111L,
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            authorizationSignature,
            prepare.getAuthorityAddress()))
        .thenReturn(true);
    when(executionSignatureVerifier.verify(
            prepare.getAuthorityAddress(),
            prepare.getPrepareId(),
            "0x" + "a".repeat(64),
            BigInteger.valueOf(prepare.getAuthExpiresAt().toEpochSecond(java.time.ZoneOffset.UTC)),
            executionSignature))
        .thenReturn(true);
    when(reserveNoncePort.reserveNextNonce("0x" + "f".repeat(40))).thenReturn(99L);
    when(eip7702TransactionCodecPort.signAndEncode(any()))
        .thenReturn(new Eip7702TransactionCodecPort.SignedPayload("0xraw", "0x" + "1".repeat(64)));
    when(transferTransactionPersistencePort.createAndFlush(any()))
        .thenReturn(
            TransferTransaction.builder()
                .id(92L)
                .idempotencyKey(prepare.getIdempotencyKey())
                .referenceType(Web3ReferenceType.LEVEL_UP_REWARD)
                .referenceId("101")
                .fromUserId(7L)
                .toUserId(22L)
                .fromAddress("0x" + "f".repeat(40))
                .toAddress(prepare.getAuthorityAddress())
                .amountWei(prepare.getAmountWei())
                .txType(Web3TxType.EIP7702)
                .status(Web3TxStatus.CREATED)
                .build());
    when(web3ContractPort.broadcast(any()))
        .thenReturn(new Web3ContractPort.BroadcastResult(true, null, null, "rpc-main"));

    SubmitTokenTransferResult result = service.execute(command);

    assertThat(result.status()).isEqualTo(Web3TxStatus.PENDING.name());
    verify(sponsorDailyUsagePersistencePort, never()).create(any());
    verify(sponsorDailyUsagePersistencePort).update(any());
  }

  @Test
  void execute_throws_whenDelegateNotAllowlisted() {
    String authorizationSignature = "0x" + "b".repeat(130);
    String executionSignature = "0x" + "c".repeat(130);
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(7L, "prepare-1", authorizationSignature, executionSignature);
    TransferPrepare prepare =
        activePrepare(
            "domain:LEVEL_UP_REWARD:101:7", TokenTransferReferenceType.SERVER_TO_USER, "101");
    TransferPrepare prepareWithDifferentDelegate =
        prepare.toBuilder().delegateTarget("0x" + "d".repeat(40)).build();
    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findForUpdateByPrepareId("prepare-1"))
        .thenReturn(Optional.of(prepareWithDifferentDelegate));
    when(transferTransactionPersistencePort.findByIdempotencyKey(prepare.getIdempotencyKey()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(command)).isInstanceOf(Web3TransferException.class);
  }

  @Test
  void execute_throws_whenAmountOverMaxTransferLimit() {
    String authorizationSignature = "0x" + "b".repeat(130);
    String executionSignature = "0x" + "c".repeat(130);
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(7L, "prepare-1", authorizationSignature, executionSignature);
    TransferPrepare prepare =
        activePrepare(
                "domain:LEVEL_UP_REWARD:101:7", TokenTransferReferenceType.SERVER_TO_USER, "101")
            .toBuilder()
            .amountWei(new BigInteger("600000000000000000"))
            .build();
    stubPreSignatureFlow(
        prepare, authorizationSignature, BigInteger.valueOf(100_000), BigInteger.valueOf(2));

    assertThatThrownBy(() -> service.execute(command)).isInstanceOf(Web3TransferException.class);
  }

  @Test
  void execute_throws_whenEstimatedCostOverPerTxCap() {
    String authorizationSignature = "0x" + "b".repeat(130);
    String executionSignature = "0x" + "c".repeat(130);
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(7L, "prepare-1", authorizationSignature, executionSignature);
    TransferPrepare prepare =
        activePrepare(
            "domain:LEVEL_UP_REWARD:101:7", TokenTransferReferenceType.SERVER_TO_USER, "101");
    stubPreSignatureFlow(
        prepare,
        authorizationSignature,
        BigInteger.valueOf(100_000),
        BigInteger.valueOf(2_000_000_000_000L));

    assertThatThrownBy(() -> service.execute(command)).isInstanceOf(Web3TransferException.class);
  }

  @Test
  void execute_updatesQuestionRewardIntentStatus_whenQuestionRewardDuplicateFound() {
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(7L, "prepare-1", "0xabc", "0xdef");
    TransferPrepare prepare =
        activePrepare(
            "domain:QUESTION_REWARD:101:7", TokenTransferReferenceType.USER_TO_USER, "101");
    QuestionRewardIntent intent =
        QuestionRewardIntent.builder()
            .id(1L)
            .postId(101L)
            .acceptedCommentId(201L)
            .fromUserId(7L)
            .toUserId(22L)
            .amountWei(BigInteger.TEN)
            .status(QuestionRewardIntentStatus.PREPARE_REQUIRED)
            .build();
    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findForUpdateByPrepareId("prepare-1"))
        .thenReturn(Optional.of(prepare));
    when(questionRewardIntentPersistencePort.findForUpdateByPostId(101L))
        .thenReturn(Optional.of(intent));
    when(transferTransactionPersistencePort.findByIdempotencyKey(prepare.getIdempotencyKey()))
        .thenReturn(
            Optional.of(
                TransferTransaction.builder()
                    .id(44L)
                    .idempotencyKey(prepare.getIdempotencyKey())
                    .referenceType(Web3ReferenceType.USER_TO_USER)
                    .referenceId("101")
                    .fromUserId(7L)
                    .toUserId(22L)
                    .fromAddress("0x" + "a".repeat(40))
                    .toAddress("0x" + "b".repeat(40))
                    .amountWei(BigInteger.TEN)
                    .txType(Web3TxType.EIP7702)
                    .status(Web3TxStatus.PENDING)
                    .txHash("0x" + "c".repeat(64))
                    .build()));

    SubmitTokenTransferResult result = service.execute(command);
    assertThat(result.transactionId()).isEqualTo(44L);

    verify(questionRewardIntentPersistencePort)
        .updateStatusIfCurrentIn(
            101L,
            momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus
                .SUBMITTED,
            EnumSet.of(
                momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus
                    .PREPARE_REQUIRED,
                momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus
                    .FAILED_ONCHAIN));
  }

  @Test
  void execute_doesNotTouchQuestionRewardIntent_whenNonQuestionRewardDuplicateFound() {
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(7L, "prepare-1", "0xabc", "0xdef");
    TransferPrepare prepare =
        activePrepare(
            "domain:LEVEL_UP_REWARD:101:7", TokenTransferReferenceType.SERVER_TO_USER, "101");
    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findForUpdateByPrepareId("prepare-1"))
        .thenReturn(Optional.of(prepare));
    when(transferTransactionPersistencePort.findByIdempotencyKey(prepare.getIdempotencyKey()))
        .thenReturn(
            Optional.of(
                TransferTransaction.builder()
                    .id(33L)
                    .idempotencyKey(prepare.getIdempotencyKey())
                    .referenceType(Web3ReferenceType.LEVEL_UP_REWARD)
                    .referenceId("101")
                    .fromUserId(7L)
                    .toUserId(22L)
                    .fromAddress("0x" + "a".repeat(40))
                    .toAddress("0x" + "b".repeat(40))
                    .amountWei(BigInteger.TEN)
                    .txType(Web3TxType.EIP7702)
                    .status(Web3TxStatus.SIGNED)
                    .txHash("0x" + "c".repeat(64))
                    .build()));

    SubmitTokenTransferResult result = service.execute(command);
    assertThat(result.transactionId()).isEqualTo(33L);
    verify(questionRewardIntentPersistencePort, never())
        .updateStatusIfCurrentIn(anyLong(), any(), any());
    verify(questionRewardIntentPersistencePort, never()).findForUpdateByPostId(anyLong());
  }

  @Test
  void execute_duplicatePath_skipsQuestionIntentUpdate_whenPostIdNullInMarkPhase() {
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(7L, "prepare-1", "0xabc", "0xdef");
    TransferPrepare prepare =
        activePrepare("domain:any:payload", TokenTransferReferenceType.USER_TO_USER, null);
    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findForUpdateByPrepareId("prepare-1"))
        .thenReturn(Optional.of(prepare));
    when(transferTransactionPersistencePort.findByIdempotencyKey(prepare.getIdempotencyKey()))
        .thenReturn(
            Optional.of(
                TransferTransaction.builder()
                    .id(96L)
                    .idempotencyKey(prepare.getIdempotencyKey())
                    .referenceType(Web3ReferenceType.USER_TO_USER)
                    .referenceId("101")
                    .fromUserId(7L)
                    .toUserId(22L)
                    .fromAddress("0x" + "a".repeat(40))
                    .toAddress("0x" + "b".repeat(40))
                    .amountWei(BigInteger.TEN)
                    .txType(Web3TxType.EIP7702)
                    .status(Web3TxStatus.PENDING)
                    .txHash("0x" + "c".repeat(64))
                    .build()));

    try (MockedStatic<TokenTransferIdempotencyKeyFactory> mocked =
        Mockito.mockStatic(TokenTransferIdempotencyKeyFactory.class)) {
      mocked
          .when(
              () -> TokenTransferIdempotencyKeyFactory.parseDomainType(prepare.getIdempotencyKey()))
          .thenReturn(DomainReferenceType.LEVEL_UP_REWARD, DomainReferenceType.QUESTION_REWARD);

      SubmitTokenTransferResult result = service.execute(command);
      assertThat(result.transactionId()).isEqualTo(96L);
    }

    verify(questionRewardIntentPersistencePort, never())
        .updateStatusIfCurrentIn(anyLong(), any(), any());
  }

  @Test
  void execute_throws_whenQuestionRewardIntentMissing() {
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(7L, "prepare-1", "0xabc", "0xdef");
    TransferPrepare prepare =
        activePrepare(
            "domain:QUESTION_REWARD:101:7", TokenTransferReferenceType.USER_TO_USER, "101");
    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findForUpdateByPrepareId("prepare-1"))
        .thenReturn(Optional.of(prepare));
    when(questionRewardIntentPersistencePort.findForUpdateByPostId(101L))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("question reward intent not found for post");
  }

  @Test
  void execute_throws_whenDailyCapExceeded() {
    String authorizationSignature = "0x" + "b".repeat(130);
    String executionSignature = "0x" + "c".repeat(130);
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(7L, "prepare-1", authorizationSignature, executionSignature);
    TransferPrepare prepare =
        activePrepare(
            "domain:LEVEL_UP_REWARD:101:7", TokenTransferReferenceType.SERVER_TO_USER, "101");
    stubPreSignatureFlow(
        prepare,
        authorizationSignature,
        BigInteger.valueOf(100_000),
        BigInteger.valueOf(200_000_000_000L));
    when(sponsorDailyUsagePersistencePort.findForUpdate(anyLong(), any()))
        .thenReturn(
            Optional.of(
                momzzangseven.mztkbe.modules.web3.transfer.domain.model.SponsorDailyUsage.builder()
                    .userId(1L)
                    .usageDateKst(java.time.LocalDate.now())
                    .estimatedCostWei(new BigInteger("990000000000000000"))
                    .build()));

    assertThatThrownBy(() -> service.execute(command)).isInstanceOf(Web3TransferException.class);
  }

  @Test
  void execute_broadcastFailure_propagatesCustomReason() {
    String authorizationSignature = "0x" + "b".repeat(130);
    String executionSignature = "0x" + "c".repeat(130);
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(7L, "prepare-1", authorizationSignature, executionSignature);
    TransferPrepare prepare =
        activePrepare(
            "domain:LEVEL_UP_REWARD:101:7", TokenTransferReferenceType.SERVER_TO_USER, "101");

    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findForUpdateByPrepareId("prepare-1"))
        .thenReturn(Optional.of(prepare));
    when(transferTransactionPersistencePort.findByIdempotencyKey(prepare.getIdempotencyKey()))
        .thenReturn(Optional.empty());
    when(eip7702ChainPort.loadPendingAccountNonce(prepare.getAuthorityAddress()))
        .thenReturn(BigInteger.valueOf(prepare.getAuthorityNonce()));
    when(loadTreasuryKeyPort.loadByAlias("sponsor", "kek"))
        .thenReturn(
            Optional.of(
                LoadTreasuryKeyPort.TreasuryKeyMaterial.of("0x" + "f".repeat(40), "f".repeat(64))));
    Eip7702ChainPort.AuthorizationTuple authTuple =
        new Eip7702ChainPort.AuthorizationTuple(
            BigInteger.valueOf(11155111L),
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            BigInteger.ZERO,
            BigInteger.ONE,
            BigInteger.ONE);
    when(eip7702AuthorizationPort.toAuthorizationTuple(
            11155111L,
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            authorizationSignature))
        .thenReturn(authTuple);
    when(eip7702TransactionCodecPort.encodeTransferData(
            prepare.getToAddress(), prepare.getAmountWei()))
        .thenReturn("0x");
    when(eip7702TransactionCodecPort.hashCalls(any())).thenReturn("0x" + "a".repeat(64));
    when(eip7702TransactionCodecPort.encodeExecute(any(), any())).thenReturn("0x");
    when(eip7702ChainPort.estimateGasWithAuthorization(
            "0x" + "f".repeat(40),
            prepare.getAuthorityAddress(),
            "0x",
            java.util.List.of(authTuple)))
        .thenReturn(BigInteger.valueOf(100_000));
    when(eip7702ChainPort.loadSponsorFeePlan())
        .thenReturn(new Eip7702ChainPort.FeePlan(BigInteger.ONE, BigInteger.valueOf(2)));
    when(sponsorDailyUsagePersistencePort.findForUpdate(anyLong(), any()))
        .thenReturn(Optional.empty());
    when(eip7702AuthorizationPort.verifySigner(
            11155111L,
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            authorizationSignature,
            prepare.getAuthorityAddress()))
        .thenReturn(true);
    when(executionSignatureVerifier.verify(
            prepare.getAuthorityAddress(),
            prepare.getPrepareId(),
            "0x" + "a".repeat(64),
            BigInteger.valueOf(prepare.getAuthExpiresAt().toEpochSecond(java.time.ZoneOffset.UTC)),
            executionSignature))
        .thenReturn(true);
    when(reserveNoncePort.reserveNextNonce("0x" + "f".repeat(40))).thenReturn(99L);
    when(eip7702TransactionCodecPort.signAndEncode(any()))
        .thenReturn(new Eip7702TransactionCodecPort.SignedPayload("0xraw", "0x" + "1".repeat(64)));
    when(transferTransactionPersistencePort.createAndFlush(any()))
        .thenReturn(
            TransferTransaction.builder()
                .id(93L)
                .idempotencyKey(prepare.getIdempotencyKey())
                .referenceType(Web3ReferenceType.LEVEL_UP_REWARD)
                .referenceId("101")
                .fromUserId(7L)
                .toUserId(22L)
                .fromAddress("0x" + "f".repeat(40))
                .toAddress(prepare.getAuthorityAddress())
                .amountWei(prepare.getAmountWei())
                .txType(Web3TxType.EIP7702)
                .status(Web3TxStatus.CREATED)
                .build());
    when(web3ContractPort.broadcast(any()))
        .thenReturn(new Web3ContractPort.BroadcastResult(false, null, "OTHER", "rpc-main"));

    SubmitTokenTransferResult result = service.execute(command);

    assertThat(result.status()).isEqualTo(Web3TxStatus.SIGNED.name());
    verify(updateTransactionPort).scheduleRetry(eq(93L), eq("OTHER"), any(LocalDateTime.class));
  }

  @Test
  void execute_broadcastFailure_withCriticalReason_returnsSigned() {
    String authorizationSignature = "0x" + "b".repeat(130);
    String executionSignature = "0x" + "c".repeat(130);
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(7L, "prepare-1", authorizationSignature, executionSignature);
    TransferPrepare prepare =
        activePrepare(
            "domain:LEVEL_UP_REWARD:101:7", TokenTransferReferenceType.SERVER_TO_USER, "101");

    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findForUpdateByPrepareId("prepare-1"))
        .thenReturn(Optional.of(prepare));
    when(transferTransactionPersistencePort.findByIdempotencyKey(prepare.getIdempotencyKey()))
        .thenReturn(Optional.empty());
    when(eip7702ChainPort.loadPendingAccountNonce(prepare.getAuthorityAddress()))
        .thenReturn(BigInteger.valueOf(prepare.getAuthorityNonce()));
    when(loadTreasuryKeyPort.loadByAlias("sponsor", "kek"))
        .thenReturn(
            Optional.of(
                LoadTreasuryKeyPort.TreasuryKeyMaterial.of("0x" + "f".repeat(40), "f".repeat(64))));
    Eip7702ChainPort.AuthorizationTuple authTuple =
        new Eip7702ChainPort.AuthorizationTuple(
            BigInteger.valueOf(11155111L),
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            BigInteger.ZERO,
            BigInteger.ONE,
            BigInteger.ONE);
    when(eip7702AuthorizationPort.toAuthorizationTuple(
            11155111L,
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            authorizationSignature))
        .thenReturn(authTuple);
    when(eip7702TransactionCodecPort.encodeTransferData(
            prepare.getToAddress(), prepare.getAmountWei()))
        .thenReturn("0x");
    when(eip7702TransactionCodecPort.hashCalls(any())).thenReturn("0x" + "a".repeat(64));
    when(eip7702TransactionCodecPort.encodeExecute(any(), any())).thenReturn("0x");
    when(eip7702ChainPort.estimateGasWithAuthorization(
            "0x" + "f".repeat(40),
            prepare.getAuthorityAddress(),
            "0x",
            java.util.List.of(authTuple)))
        .thenReturn(BigInteger.valueOf(100_000));
    when(eip7702ChainPort.loadSponsorFeePlan())
        .thenReturn(new Eip7702ChainPort.FeePlan(BigInteger.ONE, BigInteger.valueOf(2)));
    when(sponsorDailyUsagePersistencePort.findForUpdate(anyLong(), any()))
        .thenReturn(Optional.empty());
    when(eip7702AuthorizationPort.verifySigner(
            11155111L,
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            authorizationSignature,
            prepare.getAuthorityAddress()))
        .thenReturn(true);
    when(executionSignatureVerifier.verify(
            prepare.getAuthorityAddress(),
            prepare.getPrepareId(),
            "0x" + "a".repeat(64),
            BigInteger.valueOf(prepare.getAuthExpiresAt().toEpochSecond(java.time.ZoneOffset.UTC)),
            executionSignature))
        .thenReturn(true);
    when(reserveNoncePort.reserveNextNonce("0x" + "f".repeat(40))).thenReturn(99L);
    when(eip7702TransactionCodecPort.signAndEncode(any()))
        .thenReturn(new Eip7702TransactionCodecPort.SignedPayload("0xraw", "0x" + "1".repeat(64)));
    when(transferTransactionPersistencePort.createAndFlush(any()))
        .thenReturn(
            TransferTransaction.builder()
                .id(94L)
                .idempotencyKey(prepare.getIdempotencyKey())
                .referenceType(Web3ReferenceType.LEVEL_UP_REWARD)
                .referenceId("101")
                .fromUserId(7L)
                .toUserId(22L)
                .fromAddress("0x" + "f".repeat(40))
                .toAddress(prepare.getAuthorityAddress())
                .amountWei(prepare.getAmountWei())
                .txType(Web3TxType.EIP7702)
                .status(Web3TxStatus.CREATED)
                .build());
    when(web3ContractPort.broadcast(any()))
        .thenReturn(
            new Web3ContractPort.BroadcastResult(
                false,
                null,
                momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason
                    .TREASURY_ETH_BELOW_CRITICAL
                    .code(),
                "rpc-main"));

    SubmitTokenTransferResult result = service.execute(command);

    assertThat(result.status()).isEqualTo(Web3TxStatus.SIGNED.name());
  }

  @Test
  void execute_throws_whenQuestionRewardIntentExistsAndMismatches() {
    SubmitTokenTransferCommand command =
        new SubmitTokenTransferCommand(7L, "prepare-1", "0xabc", "0xdef");
    TransferPrepare prepare =
        activePrepare(
            "domain:QUESTION_REWARD:101:7", TokenTransferReferenceType.USER_TO_USER, "101");
    QuestionRewardIntent intent =
        QuestionRewardIntent.builder()
            .id(1L)
            .postId(101L)
            .acceptedCommentId(201L)
            .fromUserId(7L)
            .toUserId(999L)
            .amountWei(BigInteger.TEN)
            .status(QuestionRewardIntentStatus.PREPARE_REQUIRED)
            .build();
    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findForUpdateByPrepareId("prepare-1"))
        .thenReturn(Optional.of(prepare));
    when(questionRewardIntentPersistencePort.findForUpdateByPostId(101L))
        .thenReturn(Optional.of(intent));

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(Web3InvalidInputException.class);
  }

  private void stubPreSignatureFlow(
      TransferPrepare prepare,
      String authorizationSignature,
      BigInteger estimatedGas,
      BigInteger maxFeePerGas) {
    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findForUpdateByPrepareId("prepare-1"))
        .thenReturn(Optional.of(prepare));
    when(transferTransactionPersistencePort.findByIdempotencyKey(prepare.getIdempotencyKey()))
        .thenReturn(Optional.empty());
    when(eip7702ChainPort.loadPendingAccountNonce(prepare.getAuthorityAddress()))
        .thenReturn(BigInteger.valueOf(prepare.getAuthorityNonce()));
    when(loadTreasuryKeyPort.loadByAlias("sponsor", "kek"))
        .thenReturn(
            Optional.of(
                LoadTreasuryKeyPort.TreasuryKeyMaterial.of("0x" + "f".repeat(40), "f".repeat(64))));

    Eip7702ChainPort.AuthorizationTuple authTuple =
        new Eip7702ChainPort.AuthorizationTuple(
            BigInteger.valueOf(11155111L),
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            BigInteger.ZERO,
            BigInteger.ONE,
            BigInteger.ONE);
    when(eip7702AuthorizationPort.toAuthorizationTuple(
            11155111L,
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            authorizationSignature))
        .thenReturn(authTuple);
    when(eip7702TransactionCodecPort.encodeTransferData(
            prepare.getToAddress(), prepare.getAmountWei()))
        .thenReturn("0x");
    when(eip7702TransactionCodecPort.hashCalls(any())).thenReturn("0x" + "a".repeat(64));
    when(eip7702TransactionCodecPort.encodeExecute(any(), any())).thenReturn("0x");
    when(eip7702ChainPort.estimateGasWithAuthorization(
            "0x" + "f".repeat(40),
            prepare.getAuthorityAddress(),
            "0x",
            java.util.List.of(authTuple)))
        .thenReturn(estimatedGas);
    when(eip7702ChainPort.loadSponsorFeePlan())
        .thenReturn(new Eip7702ChainPort.FeePlan(BigInteger.ONE, maxFeePerGas));
    lenient()
        .when(sponsorDailyUsagePersistencePort.findForUpdate(anyLong(), any()))
        .thenReturn(Optional.empty());
  }

  private TransferPrepare submittedPrepare(Long fromUserId, Long submittedTxId) {
    return TransferPrepare.builder()
        .prepareId("prepare-1")
        .fromUserId(fromUserId)
        .toUserId(22L)
        .acceptedCommentId(201L)
        .referenceType(TokenTransferReferenceType.USER_TO_USER)
        .referenceId("101")
        .idempotencyKey("domain:QUESTION_REWARD:101:7")
        .authorityAddress("0x" + "a".repeat(40))
        .toAddress("0x" + "b".repeat(40))
        .amountWei(BigInteger.TEN)
        .authorityNonce(5L)
        .delegateTarget("0x" + "b".repeat(40))
        .authExpiresAt(LocalDateTime.now().plusMinutes(5))
        .payloadHashToSign("0x" + "d".repeat(64))
        .salt("0x" + "e".repeat(64))
        .status(TransferPrepareStatus.SUBMITTED)
        .submittedTxId(submittedTxId)
        .build();
  }

  private TransferPrepare activePrepare(
      String idempotencyKey, TokenTransferReferenceType referenceType, String referenceId) {
    return TransferPrepare.builder()
        .prepareId("prepare-1")
        .fromUserId(7L)
        .toUserId(22L)
        .acceptedCommentId(201L)
        .referenceType(referenceType)
        .referenceId(referenceId)
        .idempotencyKey(idempotencyKey)
        .authorityAddress("0x" + "a".repeat(40))
        .toAddress("0x" + "b".repeat(40))
        .amountWei(BigInteger.TEN)
        .authorityNonce(5L)
        .delegateTarget("0x" + "b".repeat(40))
        .authExpiresAt(LocalDateTime.now().plusMinutes(5))
        .payloadHashToSign("0x" + "d".repeat(64))
        .salt("0x" + "e".repeat(64))
        .status(TransferPrepareStatus.CREATED)
        .build();
  }

  private TransferRuntimeConfig runtimeConfig() {
    return new TransferRuntimeConfig(
        11155111L,
        "0x" + "a".repeat(40),
        30,
        "0x" + "b".repeat(40),
        "0x" + "c".repeat(40),
        "sponsor",
        "kek",
        1_000_000L,
        new BigDecimal("0.5"),
        new BigDecimal("0.1"),
        new BigDecimal("1.0"),
        600,
        "Asia/Seoul",
        7,
        100);
  }
}
