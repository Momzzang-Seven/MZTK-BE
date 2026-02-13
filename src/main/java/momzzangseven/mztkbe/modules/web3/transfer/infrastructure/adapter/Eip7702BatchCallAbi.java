package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.DynamicStruct;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Hash;

/** ABI helper for BatchImplementation.execute(Call[], bytes). */
public final class Eip7702BatchCallAbi {

  private Eip7702BatchCallAbi() {}

  public static String encodeExecute(List<Call> calls, byte[] executionSignature) {
    DynamicStruct[] callStructs =
        calls == null
            ? new DynamicStruct[0]
            : calls.stream().map(Call::toAbiStruct).toArray(DynamicStruct[]::new);

    @SuppressWarnings("unchecked")
    DynamicArray<DynamicStruct> callArray = new DynamicArray<>(DynamicStruct.class, callStructs);

    Function function =
        new Function(
            "execute",
            Arrays.asList(callArray, new DynamicBytes(executionSignature)),
            Collections.emptyList());

    return FunctionEncoder.encode(function);
  }

  /**
   * Calculates callDataHash used in EIP-712 execution signature payload.
   *
   * <p>hash = keccak256(abi.encode(calls))
   */
  public static String hashCalls(List<Call> calls) {
    DynamicStruct[] callStructs =
        calls == null
            ? new DynamicStruct[0]
            : calls.stream().map(Call::toAbiStruct).toArray(DynamicStruct[]::new);

    @SuppressWarnings("unchecked")
    DynamicArray<DynamicStruct> callArray = new DynamicArray<>(DynamicStruct.class, callStructs);

    String abiEncoded = FunctionEncoder.encodeConstructor(List.<Type>of(callArray));
    return Hash.sha3(abiEncoded);
  }

  public record Call(String to, BigInteger value, byte[] data) {
    public DynamicStruct toAbiStruct() {
      return new DynamicStruct(new Address(to), new Uint256(value), new DynamicBytes(data));
    }
  }
}
