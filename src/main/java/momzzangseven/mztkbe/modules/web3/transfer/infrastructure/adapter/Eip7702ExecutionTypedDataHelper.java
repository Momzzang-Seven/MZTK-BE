package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.web3j.crypto.StructuredDataEncoder;
import org.web3j.utils.Numeric;

/** Helper for EIP-712 Mztk7702Execution typed-data digest generation. */
public final class Eip7702ExecutionTypedDataHelper {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private Eip7702ExecutionTypedDataHelper() {}

  public static byte[] buildDigest(
      String domainName,
      String domainVersion,
      long chainId,
      String verifyingContract,
      String prepareId,
      String callDataHash,
      BigInteger gasLimit,
      BigInteger maxFeePerGas,
      BigInteger deadlineEpochSeconds) {
    validate(
        domainName,
        domainVersion,
        chainId,
        verifyingContract,
        prepareId,
        callDataHash,
        gasLimit,
        maxFeePerGas,
        deadlineEpochSeconds);

    try {
      String typedDataJson =
          buildTypedDataJson(
              domainName,
              domainVersion,
              chainId,
              verifyingContract,
              prepareId,
              callDataHash,
              gasLimit,
              maxFeePerGas,
              deadlineEpochSeconds);
      StructuredDataEncoder encoder = new StructuredDataEncoder(typedDataJson);
      return encoder.hashStructuredData();
    } catch (Exception ex) {
      throw new Web3InvalidInputException("failed to build execution typed-data digest");
    }
  }

  private static void validate(
      String domainName,
      String domainVersion,
      long chainId,
      String verifyingContract,
      String prepareId,
      String callDataHash,
      BigInteger gasLimit,
      BigInteger maxFeePerGas,
      BigInteger deadlineEpochSeconds) {
    if (domainName == null || domainName.isBlank()) {
      throw new Web3InvalidInputException("eip712 domain name is required");
    }
    if (domainVersion == null || domainVersion.isBlank()) {
      throw new Web3InvalidInputException("eip712 domain version is required");
    }
    if (chainId <= 0) {
      throw new Web3InvalidInputException("eip712 chainId must be positive");
    }
    if (verifyingContract == null || !verifyingContract.matches("^0x[0-9a-fA-F]{40}$")) {
      throw new Web3InvalidInputException("invalid verifying contract address");
    }
    if (prepareId == null || prepareId.isBlank()) {
      throw new Web3InvalidInputException("prepareId is required");
    }
    if (callDataHash == null || !callDataHash.matches("^0x[0-9a-fA-F]{64}$")) {
      throw new Web3InvalidInputException("callDataHash must be bytes32 hex");
    }
    if (gasLimit == null || gasLimit.signum() <= 0) {
      throw new Web3InvalidInputException("gasLimit must be > 0");
    }
    if (maxFeePerGas == null || maxFeePerGas.signum() <= 0) {
      throw new Web3InvalidInputException("maxFeePerGas must be > 0");
    }
    if (deadlineEpochSeconds == null || deadlineEpochSeconds.signum() <= 0) {
      throw new Web3InvalidInputException("deadline must be > 0");
    }
  }

  private static String buildTypedDataJson(
      String domainName,
      String domainVersion,
      long chainId,
      String verifyingContract,
      String prepareId,
      String callDataHash,
      BigInteger gasLimit,
      BigInteger maxFeePerGas,
      BigInteger deadlineEpochSeconds)
      throws JsonProcessingException {
    Map<String, Object> typedData = new LinkedHashMap<>();

    Map<String, List<Map<String, String>>> types = new LinkedHashMap<>();
    types.put(
        "EIP712Domain",
        List.of(
            Map.of("name", "name", "type", "string"),
            Map.of("name", "version", "type", "string"),
            Map.of("name", "chainId", "type", "uint256"),
            Map.of("name", "verifyingContract", "type", "address")));
    types.put(
        "Mztk7702Execution",
        List.of(
            Map.of("name", "prepareId", "type", "string"),
            Map.of("name", "callDataHash", "type", "bytes32"),
            Map.of("name", "gasLimit", "type", "uint256"),
            Map.of("name", "maxFeePerGas", "type", "uint256"),
            Map.of("name", "deadline", "type", "uint256")));
    typedData.put("types", types);
    typedData.put("primaryType", "Mztk7702Execution");

    Map<String, Object> domain = new LinkedHashMap<>();
    domain.put("name", domainName);
    domain.put("version", domainVersion);
    domain.put("chainId", chainId);
    domain.put(
        "verifyingContract", Numeric.prependHexPrefix(Numeric.cleanHexPrefix(verifyingContract)));
    typedData.put("domain", domain);

    Map<String, Object> message = new LinkedHashMap<>();
    message.put("prepareId", prepareId);
    message.put("callDataHash", Numeric.prependHexPrefix(Numeric.cleanHexPrefix(callDataHash)));
    message.put("gasLimit", gasLimit);
    message.put("maxFeePerGas", maxFeePerGas);
    message.put("deadline", deadlineEpochSeconds);
    typedData.put("message", message);

    return OBJECT_MAPPER.writeValueAsString(typedData);
  }
}
