package momzzangseven.mztkbe.modules.web3.eip7702.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Eip7702BatchCallAbi")
class Eip7702BatchCallAbiTest {

  private static final String TARGET = "0x" + "a".repeat(40);
  private static final String SECOND_TARGET = "0x" + "b".repeat(40);
  private static final byte[] DATA =
      new byte[] {(byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef};
  private static final byte[] SECOND_DATA = new byte[] {0x12, 0x34};
  private static final byte[] SIGNATURE = new byte[] {1, 2, 3, 4};

  @Test
  @DisplayName(
      "encodeExecute — 새 BatchImplementation execute(Call[], string, uint256, bytes) ABI selector")
  void encodeExecute_usesNewExecuteAbiSelector() {
    String encoded =
        Eip7702BatchCallAbi.encodeExecute(
            List.of(
                new Eip7702BatchCallAbi.Call(TARGET, BigInteger.ZERO, DATA),
                new Eip7702BatchCallAbi.Call(SECOND_TARGET, BigInteger.valueOf(7), SECOND_DATA)),
            "intent-123",
            BigInteger.valueOf(1_700_000_000L),
            SIGNATURE);

    assertThat(encoded).startsWith(Eip7702ExecuteCalldataAssert.executeSelector());
    assertThat(encoded)
        .doesNotStartWith(
            Eip7702ExecuteCalldataAssert.selector("execute((address,uint256,bytes)[],bytes)"));
    Eip7702ExecuteCalldataAssert.assertExecuteArguments(
        encoded,
        List.of(
            new Eip7702ExecuteCalldataAssert.ExpectedCall(TARGET, BigInteger.ZERO, DATA),
            new Eip7702ExecuteCalldataAssert.ExpectedCall(
                SECOND_TARGET, BigInteger.valueOf(7), SECOND_DATA)),
        "intent-123",
        BigInteger.valueOf(1_700_000_000L),
        SIGNATURE);
  }

  @Test
  @DisplayName("hashCalls — Solidity keccak256(abi.encode(Call[])) 값을 유지")
  void hashCalls_matchesSolidityAbiEncodedCallArrayHash() {
    List<Eip7702BatchCallAbi.Call> calls =
        List.of(
            new Eip7702BatchCallAbi.Call(TARGET, BigInteger.ZERO, DATA),
            new Eip7702BatchCallAbi.Call(SECOND_TARGET, BigInteger.valueOf(7), SECOND_DATA));

    String hash = Eip7702BatchCallAbi.hashCalls(calls);

    assertThat(hash)
        .isEqualTo("0x167349f2a5f87bf93c2e79bf734c704392f48f7b4775778d8078663dbfad9d1d");
  }
}
