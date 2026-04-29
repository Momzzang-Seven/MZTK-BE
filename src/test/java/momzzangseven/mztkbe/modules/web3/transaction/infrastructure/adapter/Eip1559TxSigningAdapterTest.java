package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySigner;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.adapter.Erc20TransferCalldataEncoder;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.service.SignEip1559TxService;
import momzzangseven.mztkbe.modules.web3.transaction.domain.encoder.Eip1559TxEncoder.Eip1559Fields;
import momzzangseven.mztkbe.modules.web3.transaction.domain.encoder.Eip1559TxEncoder.SignedTx;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Eip1559TxSigningAdapter")
class Eip1559TxSigningAdapterTest {

  private static final String TOKEN_CONTRACT = "0x" + "a".repeat(40);
  private static final String RECIPIENT = "0x" + "b".repeat(40);
  private static final String TREASURY_ADDRESS = "0x" + "c".repeat(40);
  private static final String KMS_KEY_ID = "alias/reward-treasury";
  private static final String WALLET_ALIAS = "reward-treasury";
  private static final long CHAIN_ID = 11155111L;
  private static final long NONCE = 42L;
  private static final BigInteger GAS_LIMIT = BigInteger.valueOf(60_000L);
  private static final BigInteger MAX_PRIORITY_FEE = BigInteger.valueOf(1_000_000_000L);
  private static final BigInteger MAX_FEE = BigInteger.valueOf(2_000_000_000L);
  private static final BigInteger AMOUNT_WEI = BigInteger.valueOf(1_000L);

  @Mock private SignEip1559TxService signEip1559TxService;

  @InjectMocks private Eip1559TxSigningAdapter adapter;

  private static Web3ContractPort.SignTransferCommand command() {
    TreasurySigner signer = new TreasurySigner(WALLET_ALIAS, KMS_KEY_ID, TREASURY_ADDRESS);
    return new Web3ContractPort.SignTransferCommand(
        signer,
        TOKEN_CONTRACT,
        RECIPIENT,
        AMOUNT_WEI,
        NONCE,
        CHAIN_ID,
        GAS_LIMIT,
        MAX_PRIORITY_FEE,
        MAX_FEE);
  }

  @Test
  @DisplayName("Eip1559Fields 의 to 는 토큰 컨트랙트 주소이고 value 는 0, data 는 ERC-20 transfer 칼데이터다")
  void signTransfer_buildsErc20TransferFieldsAndDelegatesToService() {
    SignedTx canned = new SignedTx("0xdeadbeef", "0x" + "d".repeat(64));
    when(signEip1559TxService.sign(any(Eip1559Fields.class), eq(KMS_KEY_ID), eq(TREASURY_ADDRESS)))
        .thenReturn(canned);

    Web3ContractPort.SignedTransaction result = adapter.signTransfer(command());

    ArgumentCaptor<Eip1559Fields> fieldsCaptor = ArgumentCaptor.forClass(Eip1559Fields.class);
    verify(signEip1559TxService).sign(fieldsCaptor.capture(), eq(KMS_KEY_ID), eq(TREASURY_ADDRESS));

    Eip1559Fields fields = fieldsCaptor.getValue();
    // The most regressable invariant: the EIP-1559 envelope `to` MUST be the ERC-20 token contract,
    // not the recipient EOA. The recipient is encoded inside the transfer calldata.
    assertThat(fields.to()).isEqualTo(TOKEN_CONTRACT);
    assertThat(fields.value()).isEqualTo(BigInteger.ZERO);
    assertThat(fields.chainId()).isEqualTo(CHAIN_ID);
    assertThat(fields.nonce()).isEqualTo(NONCE);
    assertThat(fields.gasLimit()).isEqualTo(GAS_LIMIT);
    assertThat(fields.maxPriorityFeePerGas()).isEqualTo(MAX_PRIORITY_FEE);
    assertThat(fields.maxFeePerGas()).isEqualTo(MAX_FEE);
    assertThat(fields.data())
        .isEqualTo(Erc20TransferCalldataEncoder.encodeTransferData(RECIPIENT, AMOUNT_WEI));

    assertThat(result.rawTx()).isEqualTo(canned.rawTx());
    assertThat(result.txHash()).isEqualTo(canned.txHash());
  }

  @Test
  @DisplayName("calldata 디코드 시 함수 selector 는 transfer(address,uint256) 의 0xa9059cbb 다")
  void signTransfer_calldataStartsWithErc20TransferSelector() {
    when(signEip1559TxService.sign(any(Eip1559Fields.class), eq(KMS_KEY_ID), eq(TREASURY_ADDRESS)))
        .thenReturn(new SignedTx("0xdeadbeef", "0x" + "d".repeat(64)));

    adapter.signTransfer(command());

    ArgumentCaptor<Eip1559Fields> fieldsCaptor = ArgumentCaptor.forClass(Eip1559Fields.class);
    verify(signEip1559TxService).sign(fieldsCaptor.capture(), eq(KMS_KEY_ID), eq(TREASURY_ADDRESS));

    String data = fieldsCaptor.getValue().data();
    assertThat(data).startsWith("0xa9059cbb");
    // address (20 bytes left-padded to 32) + uint256 (32 bytes) = 64 bytes = 128 hex chars,
    // plus the 4-byte selector (8 hex chars) and the "0x" prefix = 138 chars total.
    assertThat(data).hasSize(138);
  }
}
