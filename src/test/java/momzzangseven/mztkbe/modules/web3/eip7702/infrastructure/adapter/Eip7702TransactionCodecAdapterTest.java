package momzzangseven.mztkbe.modules.web3.eip7702.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702ChainPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.service.SignEip7702TxService;
import momzzangseven.mztkbe.modules.web3.eip7702.domain.encoder.Eip7702TxEncoder;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySigner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Eip7702TransactionCodecAdapter (codec port → SignEip7702TxService bridging)")
class Eip7702TransactionCodecAdapterTest {

  private static final String TO = "0x" + "a".repeat(40);
  private static final String DATA = "0xdeadbeef";

  @Mock private SignEip7702TxService signEip7702TxService;

  @InjectMocks private Eip7702TransactionCodecAdapter adapter;

  private static TreasurySigner signer() {
    return new TreasurySigner("sponsor-treasury", "alias/sponsor-treasury", "0x" + "3".repeat(40));
  }

  private static Eip7702ChainPort.AuthorizationTuple infraAuth() {
    return new Eip7702ChainPort.AuthorizationTuple(
        BigInteger.valueOf(11155111L),
        "0x" + "b".repeat(40),
        BigInteger.ONE,
        BigInteger.ZERO,
        BigInteger.ONE,
        BigInteger.TWO);
  }

  @Test
  @DisplayName(
      "signAndEncode — SignCommand 를 Eip7702Fields/TreasurySigner 로 변환해 SignEip7702TxService 에 위임")
  void signAndEncode_translatesFieldsAndDelegatesToService() {
    TreasurySigner signer = signer();
    Eip7702TransactionCodecPort.SignCommand command =
        new Eip7702TransactionCodecPort.SignCommand(
            11155111L,
            BigInteger.ZERO,
            BigInteger.valueOf(1_000_000_000L),
            BigInteger.valueOf(2_000_000_000L),
            BigInteger.valueOf(120_000),
            TO,
            BigInteger.ZERO,
            DATA,
            List.of(infraAuth()),
            signer);

    Eip7702TxEncoder.SignedTx canned =
        new Eip7702TxEncoder.SignedTx("0x04abcdef", "0x" + "f".repeat(64));
    when(signEip7702TxService.sign(any(Eip7702TxEncoder.Eip7702Fields.class), any()))
        .thenReturn(canned);

    Eip7702TransactionCodecPort.SignedPayload result = adapter.signAndEncode(command);

    ArgumentCaptor<Eip7702TxEncoder.Eip7702Fields> fieldsCaptor =
        ArgumentCaptor.forClass(Eip7702TxEncoder.Eip7702Fields.class);
    ArgumentCaptor<TreasurySigner> signerCaptor = ArgumentCaptor.forClass(TreasurySigner.class);
    org.mockito.Mockito.verify(signEip7702TxService)
        .sign(fieldsCaptor.capture(), signerCaptor.capture());

    Eip7702TxEncoder.Eip7702Fields fields = fieldsCaptor.getValue();
    assertThat(fields.chainId()).isEqualTo(11155111L);
    assertThat(fields.nonce()).isEqualTo(BigInteger.ZERO);
    assertThat(fields.maxPriorityFeePerGas()).isEqualTo(BigInteger.valueOf(1_000_000_000L));
    assertThat(fields.maxFeePerGas()).isEqualTo(BigInteger.valueOf(2_000_000_000L));
    assertThat(fields.gasLimit()).isEqualTo(BigInteger.valueOf(120_000));
    assertThat(fields.to()).isEqualTo(TO);
    assertThat(fields.value()).isEqualTo(BigInteger.ZERO);
    assertThat(fields.data()).isEqualTo(DATA);
    assertThat(fields.authorizationList()).hasSize(1);

    Eip7702TxEncoder.AuthorizationTuple translated = fields.authorizationList().get(0);
    assertThat(translated.chainId()).isEqualTo(11155111L);
    assertThat(translated.address()).isEqualTo("0x" + "b".repeat(40));
    assertThat(translated.nonce()).isEqualTo(BigInteger.ONE);
    assertThat(translated.yParity()).isEqualTo((byte) 0);
    // r=1, s=2 must be 32-byte left-padded big-endian.
    assertThat(translated.r()).hasSize(32);
    assertThat(translated.r()[31]).isEqualTo((byte) 1);
    assertThat(translated.s()).hasSize(32);
    assertThat(translated.s()[31]).isEqualTo((byte) 2);

    assertThat(signerCaptor.getValue()).isSameAs(signer);
    assertThat(result.rawTx()).isEqualTo(canned.rawTx());
    assertThat(result.txHash()).isEqualTo(canned.txHash());
  }

  @Test
  @DisplayName("SignCommand — sponsorSigner 가 null 이면 Web3InvalidInputException")
  void signCommand_throws_whenSponsorSignerNull() {
    assertThatThrownBy(
            () ->
                new Eip7702TransactionCodecPort.SignCommand(
                    11155111L,
                    BigInteger.ZERO,
                    BigInteger.valueOf(1_000_000_000L),
                    BigInteger.valueOf(2_000_000_000L),
                    BigInteger.valueOf(120_000),
                    TO,
                    BigInteger.ZERO,
                    DATA,
                    List.of(infraAuth()),
                    null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("sponsorSigner is required");
  }
}
