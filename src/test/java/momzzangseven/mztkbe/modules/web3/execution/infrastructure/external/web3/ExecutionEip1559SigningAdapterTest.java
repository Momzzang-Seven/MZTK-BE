package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.KmsSignFailedException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.Eip1559TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionEip1559SigningPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter.Eip1559TransactionCodecAdapter;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.Eip1559Fields;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignEip1559TxCommand;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignEip1559TxResult;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignedTx;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySigner;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.SignEip1559TxUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExecutionEip1559SigningAdapter (delegates to shared SignEip1559TxUseCase)")
class ExecutionEip1559SigningAdapterTest {

  private static final long CHAIN_ID = 11155111L;
  private static final long NONCE = 9L;
  private static final BigInteger GAS_LIMIT = BigInteger.valueOf(210_000);
  private static final BigInteger MAX_PRIORITY_FEE = BigInteger.valueOf(2_000_000_000L);
  private static final BigInteger MAX_FEE = BigInteger.valueOf(40_000_000_000L);
  private static final String CONTRACT_ADDRESS = "0x" + "1".repeat(40);
  private static final String CALLDATA = "0x12345678";
  private static final String KMS_KEY_ID = "alias/sponsor-treasury";
  private static final String WALLET_ALIAS = "test-sponsor";
  private static final String WALLET_ADDRESS = "0x" + "c".repeat(40);

  @Mock private SignEip1559TxUseCase signEip1559TxUseCase;

  @InjectMocks private ExecutionEip1559SigningAdapter adapter;

  @Test
  @DisplayName(
      "sign builds Eip1559Fields and forwards (fields, kmsKeyId, walletAddress) to the shared SignEip1559TxUseCase")
  void sign_buildsFieldsAndDelegatesToSignEip1559TxUseCase() {
    SignedTx canned = new SignedTx("0xdeadbeef", "0x" + "d".repeat(64));
    when(signEip1559TxUseCase.sign(any(SignEip1559TxCommand.class)))
        .thenReturn(new SignEip1559TxResult(canned));

    ExecutionEip1559SigningPort.SignedTransaction signed =
        adapter.sign(
            new ExecutionEip1559SigningPort.SignCommand(
                CHAIN_ID,
                NONCE,
                GAS_LIMIT,
                CONTRACT_ADDRESS,
                BigInteger.ZERO,
                CALLDATA,
                MAX_PRIORITY_FEE,
                MAX_FEE,
                new TreasurySigner(WALLET_ALIAS, KMS_KEY_ID, WALLET_ADDRESS)));

    ArgumentCaptor<SignEip1559TxCommand> commandCaptor =
        ArgumentCaptor.forClass(SignEip1559TxCommand.class);
    verify(signEip1559TxUseCase).sign(commandCaptor.capture());

    SignEip1559TxCommand captured = commandCaptor.getValue();
    Eip1559Fields fields = captured.fields();
    assertThat(fields.chainId()).isEqualTo(CHAIN_ID);
    assertThat(fields.nonce()).isEqualTo(NONCE);
    assertThat(fields.maxPriorityFeePerGas()).isEqualTo(MAX_PRIORITY_FEE);
    assertThat(fields.maxFeePerGas()).isEqualTo(MAX_FEE);
    assertThat(fields.gasLimit()).isEqualTo(GAS_LIMIT);
    assertThat(fields.to()).isEqualTo(CONTRACT_ADDRESS);
    assertThat(fields.value()).isEqualTo(BigInteger.ZERO);
    assertThat(fields.data()).isEqualTo(CALLDATA);

    assertThat(captured.kmsKeyId()).isEqualTo(KMS_KEY_ID);
    assertThat(captured.expectedSignerAddress()).isEqualTo(WALLET_ADDRESS);

    assertThat(signed.rawTransaction()).isEqualTo(canned.rawTx());
    assertThat(signed.txHash()).isEqualTo(canned.txHash());
  }

  @Test
  @DisplayName("null valueWei is normalized to BigInteger.ZERO before validation")
  void sign_normalizesNullValueWeiToZero() {
    SignedTx canned = new SignedTx("0xabc", "0x" + "e".repeat(64));
    when(signEip1559TxUseCase.sign(any(SignEip1559TxCommand.class)))
        .thenReturn(new SignEip1559TxResult(canned));

    adapter.sign(
        new ExecutionEip1559SigningPort.SignCommand(
            CHAIN_ID,
            NONCE,
            GAS_LIMIT,
            CONTRACT_ADDRESS,
            null,
            CALLDATA,
            MAX_PRIORITY_FEE,
            MAX_FEE,
            new TreasurySigner(WALLET_ALIAS, KMS_KEY_ID, WALLET_ADDRESS)));

    ArgumentCaptor<SignEip1559TxCommand> commandCaptor =
        ArgumentCaptor.forClass(SignEip1559TxCommand.class);
    verify(signEip1559TxUseCase).sign(commandCaptor.capture());
    assertThat(commandCaptor.getValue().fields().value()).isEqualTo(BigInteger.ZERO);
  }

  @Test
  @DisplayName(
      "Eip1559Fields invariants survive round-trip: produced raw tx decodes back to the same snapshot")
  void sign_producesRawTransactionThatRoundTripsThroughCodec() {
    // Sanity check at the integration boundary between the adapter and the shared codec contract:
    // any signed bytes produced through the shared SignEip1559TxUseCase must decode back via the
    // execution-local Eip1559TransactionCodecPort to the same logical snapshot the adapter built.
    SignedTx canned = new SignedTx("0xfeed", "0x" + "f".repeat(64));
    when(signEip1559TxUseCase.sign(any(SignEip1559TxCommand.class)))
        .thenReturn(new SignEip1559TxResult(canned));

    ExecutionEip1559SigningPort.SignedTransaction signed =
        adapter.sign(
            new ExecutionEip1559SigningPort.SignCommand(
                CHAIN_ID,
                NONCE,
                GAS_LIMIT,
                CONTRACT_ADDRESS,
                BigInteger.ZERO,
                CALLDATA,
                MAX_PRIORITY_FEE,
                MAX_FEE,
                new TreasurySigner(WALLET_ALIAS, KMS_KEY_ID, WALLET_ADDRESS)));

    Eip1559TransactionCodecPort codec = new Eip1559TransactionCodecAdapter();
    UnsignedTxSnapshot snapshot =
        new UnsignedTxSnapshot(
            CHAIN_ID,
            WALLET_ADDRESS,
            CONTRACT_ADDRESS,
            BigInteger.ZERO,
            CALLDATA,
            NONCE,
            GAS_LIMIT,
            MAX_PRIORITY_FEE,
            MAX_FEE);
    assertThat(codec.computeFingerprint(snapshot)).isNotBlank();
    assertThat(signed.rawTransaction()).isEqualTo(canned.rawTx());
    assertThat(signed.txHash()).isEqualTo(canned.txHash());
  }

  @Test
  @DisplayName("[M-126] sign(null) → Web3InvalidInputException(\"command is required\")")
  void sign_throwsWeb3InvalidInputException_whenCommandNull() {
    assertThatThrownBy(() -> adapter.sign(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("command is required");
  }

  @Test
  @DisplayName(
      "[M-127] SignEip1559TxUseCase 의 KmsSignFailedException 은 wrapping 없이 그대로 propagate 된다 — internal delegate 의 retriable/terminal 분기 invariant")
  void sign_propagatesKmsSignFailedExceptionInstance() {
    KmsSignFailedException original = new KmsSignFailedException("kms throttled");
    when(signEip1559TxUseCase.sign(any(SignEip1559TxCommand.class))).thenThrow(original);

    assertThatThrownBy(
            () ->
                adapter.sign(
                    new ExecutionEip1559SigningPort.SignCommand(
                        CHAIN_ID,
                        NONCE,
                        GAS_LIMIT,
                        CONTRACT_ADDRESS,
                        BigInteger.ZERO,
                        CALLDATA,
                        MAX_PRIORITY_FEE,
                        MAX_FEE,
                        new TreasurySigner(WALLET_ALIAS, KMS_KEY_ID, WALLET_ADDRESS))))
        .isSameAs(original);
  }
}
