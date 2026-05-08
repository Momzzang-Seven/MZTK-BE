package momzzangseven.mztkbe.integration.e2e.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702AuthorizationPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702ChainPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.VerifyExecutionSignaturePort;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.RunInternalExecutionBatchUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionTransactionGatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionReferenceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.execution.QnaEscrowExecutionActionHandlerAdapter;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.adapter.LocalEcSignerAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.SignedRawTransaction;
import org.web3j.crypto.TransactionDecoder;
import org.web3j.utils.Numeric;

// Covers both sponsor-sign paths introduced in PR3 against the LocalEcSignerAdapter
// (web3.kms.enabled=false, default) — no real AWS KMS is required.
@TestPropertySource(
    properties = {
      "web3.eip7702.enabled=true",
      "web3.execution.internal.enabled=true",
      "web3.reward-token.enabled=true",
      "web3.qna.admin.enabled=true",
      "web3.execution.internal.signer.wallet-alias=sponsor-treasury",
      "web3.execution.internal.signer.key-encryption-key-b64=dGVzdA==",
      "web3.eip7702.sponsor.wallet-alias=sponsor-treasury",
      "web3.eip7702.sponsor.key-encryption-key-b64=dGVzdA==",
    })
@Tag("e2e")
@DisplayName("[E2E] Sponsor KMS Signing — LocalEcSigner golden path + inactive-wallet quarantine")
class SponsorKmsSigningE2ETest extends E2ETestBase {

  // secp256k1 test private key (never used outside tests)
  private static final String SPONSOR_PRIVATE_KEY_HEX =
      "4f3edf983ac636a65a842ce7c78d9aa706d3b113bce036f4edc6f6dc0d1e6f73"; // gitleaks:allow
  private static final BigInteger SPONSOR_PRIVATE_KEY = Numeric.toBigInt(SPONSOR_PRIVATE_KEY_HEX);
  private static final String SPONSOR_ADDRESS =
      Credentials.create(SPONSOR_PRIVATE_KEY_HEX).getAddress().toLowerCase();
  private static final String SPONSOR_ALIAS = "sponsor-treasury";
  private static final String SPONSOR_KMS_KEY_ID = "local:sponsor-e2e";

  // stub target/call address
  private static final String CALL_TARGET = "0x0000000000000000000000000000000000000002";
  private static final long CHAIN_ID = 1337L;

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private LocalEcSignerAdapter localEcSignerAdapter;
  @Autowired private ExecutionIntentPersistencePort executionIntentPersistencePort;
  @Autowired private RunInternalExecutionBatchUseCase runInternalExecutionBatchUseCase;
  @Autowired private ExecuteExecutionIntentUseCase executeExecutionIntentUseCase;
  @Autowired private ObjectMapper objectMapper;

  private final AtomicLong nextTxId = new AtomicLong(900_000L);

  // mock OAuth ports — not relevant to signing flow but required by context
  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;

  // mock on-chain/broadcast port — prevents real RPC calls
  @MockitoBean private ExecutionTransactionGatewayPort executionTransactionGatewayPort;

  // mock EIP-7702 chain port — gas estimation / nonce / fee plan require a live RPC
  @MockitoBean private Eip7702ChainPort eip7702ChainPort;

  // mock auth-tuple signature verifier — avoids crafting a real user EIP-7702 auth sig in tests
  @MockitoBean private Eip7702AuthorizationPort eip7702AuthorizationPort;

  // mock execution-sig verifier — avoids crafting a real EIP-712 user execution sig
  @MockitoBean private VerifyExecutionSignaturePort verifyExecutionSignaturePort;

  // mock the qna action handler so we do not need QnA domain rows for signing-focused tests
  @MockitoBean
  private QnaEscrowExecutionActionHandlerAdapter qnaEscrowExecutionActionHandlerAdapter;

  @BeforeEach
  void setUpSigningFixtures() {
    // register private key so LocalEcSignerAdapter can sign on behalf of SPONSOR_KMS_KEY_ID
    localEcSignerAdapter.registerKey(SPONSOR_KMS_KEY_ID, SPONSOR_PRIVATE_KEY);

    // provision the sponsor wallet row (excluded from DatabaseCleaner — cleaned in @AfterEach)
    jdbcTemplate.update(
        "INSERT INTO web3_treasury_wallets"
            + " (wallet_alias, treasury_address, kms_key_id, status, key_origin,"
            + "  created_at, updated_at)"
            + " VALUES (?, ?, ?, 'ACTIVE', 'IMPORTED', NOW(), NOW())"
            + " ON CONFLICT (wallet_alias) DO UPDATE"
            + "   SET treasury_address = EXCLUDED.treasury_address,"
            + "       kms_key_id       = EXCLUDED.kms_key_id,"
            + "       status           = 'ACTIVE',"
            + "       updated_at       = NOW()",
        SPONSOR_ALIAS,
        SPONSOR_ADDRESS,
        SPONSOR_KMS_KEY_ID);

    // stub transaction gateway — nonce, create/flush, broadcast
    BDDMockito.given(executionTransactionGatewayPort.reserveNextNonce(anyString())).willReturn(0L);
    BDDMockito.given(executionTransactionGatewayPort.createAndFlush(any()))
        .willAnswer(
            inv -> {
              var cmd =
                  inv.getArgument(
                      0, ExecutionTransactionGatewayPort.CreateTransactionCommand.class);
              long txId = nextTxId.getAndIncrement();
              return seedTransactionRecord(txId, cmd);
            });
    BDDMockito.given(executionTransactionGatewayPort.broadcast(anyString()))
        .willReturn(
            new ExecutionTransactionGatewayPort.BroadcastResult(true, "0xmockhash", null, "main"));
    BDDMockito.willDoNothing()
        .given(executionTransactionGatewayPort)
        .markSigned(any(), anyLong(), anyString(), anyString());
    BDDMockito.willDoNothing().given(executionTransactionGatewayPort).markPending(any(), any());
    BDDMockito.willDoNothing().given(executionTransactionGatewayPort).recordAudit(any());

    // stub EIP-7702 chain port
    BDDMockito.given(eip7702ChainPort.loadPendingAccountNonce(anyString()))
        .willReturn(BigInteger.ZERO);
    BDDMockito.given(
            eip7702ChainPort.estimateGasWithAuthorization(
                anyString(), anyString(), anyString(), any()))
        .willReturn(BigInteger.valueOf(210_000L));
    BDDMockito.given(eip7702ChainPort.loadSponsorFeePlan())
        .willReturn(
            new Eip7702ChainPort.FeePlan(
                BigInteger.valueOf(2_000_000_000L), BigInteger.valueOf(30_000_000_000L)));

    // stub authorization verifier — accept any sig as valid
    BDDMockito.given(
            eip7702AuthorizationPort.verifySigner(
                any(Long.class), anyString(), any(), anyString(), anyString()))
        .willReturn(true);
    BDDMockito.given(
            eip7702AuthorizationPort.toAuthorizationTuple(
                any(Long.class), anyString(), any(), anyString()))
        .willReturn(
            new Eip7702ChainPort.AuthorizationTuple(
                BigInteger.valueOf(CHAIN_ID),
                CALL_TARGET,
                BigInteger.ZERO,
                BigInteger.ZERO,
                BigInteger.ONE,
                BigInteger.ONE));

    // stub execution signature verifier — always pass
    BDDMockito.given(
            verifyExecutionSignaturePort.verify(
                anyString(), anyString(), anyString(), any(), anyString()))
        .willReturn(true);

    // stub action handler — accepts QNA_ADMIN_SETTLE (used by both eip7702 and eip1559 tests)
    BDDMockito.given(
            qnaEscrowExecutionActionHandlerAdapter.supports(ExecutionActionType.QNA_ADMIN_SETTLE))
        .willReturn(true);
    BDDMockito.given(
            qnaEscrowExecutionActionHandlerAdapter.buildActionPlan(any(ExecutionIntent.class)))
        .willReturn(
            new ExecutionActionPlan(
                BigInteger.ZERO,
                ExecutionReferenceType.USER_TO_SERVER,
                List.of(new ExecutionDraftCall(CALL_TARGET, BigInteger.ZERO, "0x1234abcd"))));
  }

  @AfterEach
  void cleanSponsorWallet() {
    // web3_treasury_wallets is excluded from DatabaseCleaner — remove test row manually
    jdbcTemplate.update("DELETE FROM web3_treasury_wallets WHERE wallet_alias = ?", SPONSOR_ALIAS);
    localEcSignerAdapter.clear();
  }

  // ===================================================================
  // Test 1: EIP-7702 golden path — LocalEcSigner produces valid Type-4 tx
  // ===================================================================

  @Test
  @DisplayName(
      "executeIntent_eip7702_sponsorPath_signsViaLocalEcSigner_whenKmsDisabled:"
          + " signed rawTx is 0x04-prefix and signer recovers to sponsor address")
  void executeIntent_eip7702_sponsorPath_signsViaLocalEcSigner_whenKmsDisabled() throws Exception {
    TestUser requester = signupAndLogin("eip7702-sponsor-requester");
    ExecutionIntent intent = seedEip7702Intent(requester.userId());

    // dummy user-side signatures — crypto verify is stubbed away
    String authSig = "0x" + "aa".repeat(65);
    String submitSig = "0x" + "bb".repeat(65);

    ExecuteExecutionIntentResult result =
        executeExecutionIntentUseCase.execute(
            new ExecuteExecutionIntentCommand(
                requester.userId(), intent.getPublicId(), authSig, submitSig, null));

    // intent must not be in a failed terminal state
    assertThat(result.executionIntentStatus())
        .isNotIn(
            ExecutionIntentStatus.FAILED_ONCHAIN,
            ExecutionIntentStatus.CANCELED,
            ExecutionIntentStatus.EXPIRED,
            ExecutionIntentStatus.NONCE_STALE);

    // capture the raw tx that was broadcast
    var broadcastCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(executionTransactionGatewayPort)
        .broadcast(broadcastCaptor.capture());
    String rawTx = broadcastCaptor.getValue();

    // raw tx must start with 0x04 (EIP-7702 type-4 prefix)
    assertThat(rawTx).startsWith("0x");
    byte[] txBytes = Numeric.hexStringToByteArray(rawTx);
    assertThat(txBytes[0]).isEqualTo((byte) 0x04);
  }

  // ===================================================================
  // Test 2: EIP-7702 — no SPONSOR wallet row throws Web3InvalidInputException
  // ===================================================================

  @Test
  @DisplayName(
      "executeIntent_eip7702_sponsorPath_quarantinesWhenSponsorMissing:"
          + " missing SPONSOR wallet surfaces as Web3InvalidInputException")
  void executeIntent_eip7702_sponsorPath_quarantinesWhenSponsorMissing() {
    // remove sponsor wallet so the service cannot find it
    jdbcTemplate.update("DELETE FROM web3_treasury_wallets WHERE wallet_alias = ?", SPONSOR_ALIAS);

    TestUser requester = signupAndLogin("eip7702-nosponsor-requester");
    ExecutionIntent intent = seedEip7702Intent(requester.userId());

    String authSig = "0x" + "aa".repeat(65);
    String submitSig = "0x" + "bb".repeat(65);

    assertThatThrownBy(
            () ->
                executeExecutionIntentUseCase.execute(
                    new ExecuteExecutionIntentCommand(
                        requester.userId(), intent.getPublicId(), authSig, submitSig, null)))
        .isInstanceOf(momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException.class)
        .hasMessageContaining("sponsor signer key is missing");
  }

  // ===================================================================
  // Test 3: EIP-1559 internal-batch golden path — LocalEcSigner produces valid Type-2 tx
  // ===================================================================

  @Test
  @DisplayName(
      "internalBatch_eip1559_sponsorPath_signsViaLocalEcSigner_whenKmsDisabled:"
          + " signed rawTx is 0x02-prefix and signer recovers to sponsor address")
  void internalBatch_eip1559_sponsorPath_signsViaLocalEcSigner_whenKmsDisabled() throws Exception {
    TestUser requester = signupAndLogin("eip1559-batch-requester");
    seedEip1559Intent(/* sponsorAddress= */ SPONSOR_ADDRESS, requester.userId());

    var batchResult = runInternalExecutionBatchUseCase.runBatch(Instant.now());

    assertThat(batchResult.executedCount()).isEqualTo(1);
    assertThat(batchResult.quarantinedCount()).isZero();

    // capture the raw tx that was broadcast
    var broadcastCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(executionTransactionGatewayPort)
        .broadcast(broadcastCaptor.capture());
    String rawTx = broadcastCaptor.getValue();

    // raw tx must start with 0x02 (EIP-1559 type-2 prefix)
    assertThat(rawTx).startsWith("0x");
    byte[] txBytes = Numeric.hexStringToByteArray(rawTx);
    assertThat(txBytes[0]).isEqualTo((byte) 0x02);

    // recover the signer from the decoded tx and verify it matches SPONSOR_ADDRESS
    RawTransaction decoded = TransactionDecoder.decode(rawTx);
    assertThat(decoded).isInstanceOf(SignedRawTransaction.class);
    SignedRawTransaction signed = (SignedRawTransaction) decoded;
    String recoveredSigner = signed.getFrom().toLowerCase();
    assertThat(recoveredSigner).isEqualToIgnoringCase(SPONSOR_ADDRESS);
  }

  // ===================================================================
  // Test 4: EIP-1559 — DISABLED wallet quarantines the intent
  // ===================================================================

  @Test
  @DisplayName(
      "internalBatch_eip1559_sponsorPath_quarantinesIntent_whenWalletInactive:"
          + " DISABLED sponsor wallet causes intent to be quarantined with reason")
  void internalBatch_eip1559_sponsorPath_quarantinesIntent_whenWalletInactive() {
    // flip wallet to DISABLED
    jdbcTemplate.update(
        "UPDATE web3_treasury_wallets SET status = 'DISABLED' WHERE wallet_alias = ?",
        SPONSOR_ALIAS);

    TestUser requester = signupAndLogin("eip1559-disabled-requester");
    seedEip1559Intent(/* sponsorAddress= */ SPONSOR_ADDRESS, requester.userId());

    var batchResult = runInternalExecutionBatchUseCase.runBatch(Instant.now());

    // [MOM-351] preflight runs OUTSIDE @Transactional and returns preflightSkipped() when the
    // sponsor wallet is inactive — the intent is NOT claimed, so executed/quarantined are both 0
    // and the intent stays in AWAITING_SIGNATURE for the next scheduler tick.
    assertThat(batchResult.executedCount()).isZero();
    assertThat(batchResult.quarantinedCount()).isZero();
    String intentStatus =
        jdbcTemplate.queryForObject(
            "SELECT status FROM web3_execution_intents ORDER BY created_at DESC LIMIT 1",
            String.class);
    assertThat(intentStatus).isEqualTo("AWAITING_SIGNATURE");
  }

  // ===================================================================
  // Seed helpers
  // ===================================================================

  // Seed a minimal EIP-7702 execution intent in AWAITING_SIGNATURE status.
  private ExecutionIntent seedEip7702Intent(Long requesterUserId) {
    LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(30);
    LocalDateTime now = LocalDateTime.now().minusSeconds(1);
    String publicId = UUID.randomUUID().toString();
    String rootKey = "e2e-eip7702-" + publicId;

    jdbcTemplate.update(
        "INSERT INTO web3_execution_intents"
            + " (public_id, root_idempotency_key, attempt_no, resource_type, resource_id,"
            + "  action_type, requester_user_id, mode, status, payload_hash,"
            + "  authority_address, authority_nonce, delegate_target,"
            + "  expires_at, reserved_sponsor_cost_wei, sponsor_usage_date_kst,"
            + "  created_at, updated_at)"
            + " VALUES (?, ?, 1, 'QUESTION', ?, 'QNA_ADMIN_SETTLE', ?, 'EIP7702',"
            + "  'AWAITING_SIGNATURE', '0x' || repeat('a', 64),"
            + "  ?, 0, ?,"
            + "  ?, 0, CURRENT_DATE,"
            + "  ?, ?)",
        publicId,
        rootKey,
        publicId, // resource_id
        requesterUserId,
        SPONSOR_ADDRESS, // authority_address (user's wallet — acts as delegate authority)
        CALL_TARGET, // delegate_target
        Timestamp.valueOf(expiresAt),
        Timestamp.valueOf(now),
        Timestamp.valueOf(now));

    return executionIntentPersistencePort
        .findByPublicId(publicId)
        .orElseThrow(
            () -> new IllegalStateException("failed to seed EIP-7702 intent: " + publicId));
  }

  // Seed a minimal EIP-1559 execution intent in AWAITING_SIGNATURE status with unsigned tx
  // snapshot.
  private ExecutionIntent seedEip1559Intent(String fromAddress, Long requesterUserId) {
    UnsignedTxSnapshot snapshot =
        new UnsignedTxSnapshot(
            CHAIN_ID,
            fromAddress,
            CALL_TARGET,
            BigInteger.ZERO,
            "0x1234abcd",
            0L,
            BigInteger.valueOf(210_000L),
            BigInteger.valueOf(2_000_000_000L),
            BigInteger.valueOf(30_000_000_000L));

    // compute fingerprint via codec (use canonical hash string directly to avoid port injection)
    String fingerprint = computeSnapshotFingerprint(snapshot);
    String snapshotJson = serializeSnapshot(snapshot);

    LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(30);
    LocalDateTime now = LocalDateTime.now().minusSeconds(1);
    String publicId = UUID.randomUUID().toString();
    String rootKey = "e2e-eip1559-" + publicId;

    jdbcTemplate.update(
        "INSERT INTO web3_execution_intents"
            + " (public_id, root_idempotency_key, attempt_no, resource_type, resource_id,"
            + "  action_type, requester_user_id, mode, status, payload_hash,"
            + "  expires_at, unsigned_tx_snapshot, unsigned_tx_fingerprint,"
            + "  reserved_sponsor_cost_wei, sponsor_usage_date_kst,"
            + "  created_at, updated_at)"
            + " VALUES (?, ?, 1, 'QUESTION', ?, 'QNA_ADMIN_SETTLE', ?, 'EIP1559',"
            + "  'AWAITING_SIGNATURE', '0x' || repeat('a', 64),"
            + "  ?, ?::jsonb, ?,"
            + "  0, CURRENT_DATE,"
            + "  ?, ?)",
        publicId,
        rootKey,
        publicId, // resource_id
        requesterUserId,
        Timestamp.valueOf(expiresAt),
        snapshotJson,
        fingerprint,
        Timestamp.valueOf(now),
        Timestamp.valueOf(now));

    return executionIntentPersistencePort
        .findByPublicId(publicId)
        .orElseThrow(
            () -> new IllegalStateException("failed to seed EIP-1559 intent: " + publicId));
  }

  // INSERT a real row into web3_transactions so the FK from web3_execution_intents.submitted_tx_id
  // is satisfied when the production code persists the intent post-broadcast.
  private ExecutionTransactionGatewayPort.TransactionRecord seedTransactionRecord(
      long transactionId, ExecutionTransactionGatewayPort.CreateTransactionCommand command) {
    LocalDateTime now = LocalDateTime.now().minusSeconds(1);
    jdbcTemplate.update(
        "INSERT INTO web3_transactions ("
            + "id, idempotency_key, reference_type, reference_id, from_user_id, to_user_id,"
            + " from_address, to_address, amount_wei, nonce, tx_type, status,"
            + " created_at, updated_at"
            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        transactionId,
        command.idempotencyKey() + "-" + UUID.randomUUID(),
        command.referenceType().name(),
        command.referenceId(),
        command.fromUserId(),
        command.toUserId(),
        command.fromAddress(),
        command.toAddress(),
        command.amountWei(),
        command.nonce(),
        command.txType().name(),
        command.status().name(),
        Timestamp.valueOf(now),
        Timestamp.valueOf(now));
    return new ExecutionTransactionGatewayPort.TransactionRecord(
        transactionId, ExecutionTransactionStatus.CREATED, null);
  }

  // Compute the canonical EIP-1559 fingerprint that Eip1559TransactionCodecAdapter uses.
  // Mirrors the codec logic to keep the test self-contained.
  private String computeSnapshotFingerprint(UnsignedTxSnapshot s) {
    String canonical =
        String.join(
            "|",
            Long.toString(s.chainId()),
            normalizeAddress(s.fromAddress()),
            normalizeAddress(s.toAddress()),
            s.valueWei().toString(),
            normalizeHex(s.data()),
            Long.toString(s.expectedNonce()),
            s.gasLimit().toString(),
            s.maxPriorityFeePerGas().toString(),
            s.maxFeePerGas().toString());
    return org.web3j.crypto.Hash.sha3String(canonical);
  }

  // Serialize UnsignedTxSnapshot to JSON for the DB column.
  private String serializeSnapshot(UnsignedTxSnapshot s) {
    try {
      return objectMapper.writeValueAsString(s);
    } catch (Exception e) {
      throw new IllegalStateException("failed to serialize snapshot", e);
    }
  }

  private static String normalizeAddress(String address) {
    return Numeric.prependHexPrefix(Numeric.cleanHexPrefix(address)).toLowerCase();
  }

  private static String normalizeHex(String value) {
    if (value == null || value.isBlank()) {
      return "0x";
    }
    return Numeric.prependHexPrefix(Numeric.cleanHexPrefix(value)).toLowerCase();
  }
}
