package momzzangseven.mztkbe.modules.web3.eip7702.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

final class Eip7702ExecuteCalldataAssert {

  private static final String EXECUTE_SIGNATURE =
      "execute((address,uint256,bytes)[],string,uint256,bytes)";

  private Eip7702ExecuteCalldataAssert() {}

  static String executeSelector() {
    return Hash.sha3String(EXECUTE_SIGNATURE).substring(0, 10);
  }

  static void assertExecuteArguments(
      String encoded, String prepareId, BigInteger deadlineEpochSeconds, byte[] signature) {
    assertExecuteArguments(encoded, null, prepareId, deadlineEpochSeconds, signature);
  }

  static void assertExecuteArguments(
      String encoded,
      List<ExpectedCall> expectedCalls,
      String prepareId,
      BigInteger deadlineEpochSeconds,
      byte[] signature) {
    assertThat(encoded).startsWith(executeSelector());
    String payload = encoded.substring(10);

    if (expectedCalls != null) {
      assertCalls(payload, expectedCalls);
    }
    assertThat(slot(payload, 2)).isEqualTo(uint256(deadlineEpochSeconds));

    int prepareIdOffset = uintSlot(slot(payload, 1));
    byte[] prepareIdBytes = dynamicData(payload, prepareIdOffset);
    assertThat(new String(prepareIdBytes, StandardCharsets.UTF_8)).isEqualTo(prepareId);

    int signatureOffset = uintSlot(slot(payload, 3));
    assertThat(dynamicData(payload, signatureOffset)).containsExactly(signature);
  }

  static String selector(String methodSignature) {
    return Hash.sha3String(methodSignature).substring(0, 10);
  }

  record ExpectedCall(String to, BigInteger value, byte[] data) {}

  private static void assertCalls(String payload, List<ExpectedCall> expectedCalls) {
    int callsOffset = uintSlot(slot(payload, 0));
    int callsBase = callsOffset * 2;
    int callCount = uintSlot(payload.substring(callsBase, callsBase + 64));
    assertThat(callCount).isEqualTo(expectedCalls.size());

    for (int i = 0; i < expectedCalls.size(); i++) {
      int elementOffset =
          uintSlot(payload.substring(callsBase + ((i + 1) * 64), callsBase + ((i + 2) * 64)));
      int elementBase = callsBase + 64 + (elementOffset * 2);
      ExpectedCall expected = expectedCalls.get(i);

      assertThat(addressAt(payload, elementBase)).isEqualTo(expected.to().toLowerCase());
      assertThat(new BigInteger(payload.substring(elementBase + 64, elementBase + 128), 16))
          .isEqualTo(expected.value());

      int dataOffset = uintSlot(payload.substring(elementBase + 128, elementBase + 192));
      assertThat(dynamicData(payload, (elementBase / 2) + dataOffset))
          .containsExactly(expected.data());
    }
  }

  private static String slot(String payload, int index) {
    int start = index * 64;
    return payload.substring(start, start + 64);
  }

  private static String addressAt(String payload, int offset) {
    return "0x" + payload.substring(offset + 24, offset + 64);
  }

  private static int uintSlot(String slot) {
    return new BigInteger(slot, 16).intValueExact();
  }

  private static byte[] dynamicData(String payload, int offsetBytes) {
    int offset = offsetBytes * 2;
    int length = uintSlot(payload.substring(offset, offset + 64));
    int dataStart = offset + 64;
    return Numeric.hexStringToByteArray(
        "0x" + payload.substring(dataStart, dataStart + length * 2));
  }

  private static String uint256(BigInteger value) {
    String hex = value.toString(16);
    return "0".repeat(64 - hex.length()) + hex;
  }
}
