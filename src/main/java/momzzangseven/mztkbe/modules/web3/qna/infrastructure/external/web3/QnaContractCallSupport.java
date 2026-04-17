package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.web3;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigInteger;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.DefaultGasFeeCalculator;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.http.HttpService;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class QnaContractCallSupport {

  private final Web3CoreProperties web3CoreProperties;
  private final DefaultGasFeeCalculator defaultGasFeeCalculator;

  private Web3j mainWeb3j;
  private Web3j subWeb3j;

  @PostConstruct
  void init() {
    mainWeb3j = Web3j.build(new HttpService(web3CoreProperties.getRpc().getMain()));
    subWeb3j = Web3j.build(new HttpService(web3CoreProperties.getRpc().getSub()));
  }

  @PreDestroy
  void shutdown() {
    if (mainWeb3j != null) {
      mainWeb3j.shutdown();
    }
    if (subWeb3j != null) {
      subWeb3j.shutdown();
    }
  }

  public BigInteger loadAllowance(String ownerAddress, String spenderAddress, String tokenAddress) {
    String data =
        FunctionEncoder.encode(
            new Function(
                "allowance",
                List.of(new Address(ownerAddress), new Address(spenderAddress)),
                List.of(TypeReference.create(Uint256.class))));
    Transaction tx = Transaction.createEthCallTransaction(ownerAddress, tokenAddress, data);
    EthCall response =
        requireSuccess(
            callWithFallback(web3j -> web3j.ethCall(tx, DefaultBlockParameterName.PENDING).send()),
            "allowance");
    if (response.isReverted()) {
      throw new Web3InvalidInputException(
          "allowance eth_call reverted: " + response.getRevertReason());
    }
    return decodeUint256(response.getValue());
  }

  public boolean isSupportedToken(String escrowAddress, String tokenAddress) {
    String data =
        FunctionEncoder.encode(
            new Function(
                "isSupportedToken",
                List.of(new Address(tokenAddress)),
                List.of(TypeReference.create(Bool.class))));
    Transaction tx = Transaction.createEthCallTransaction(tokenAddress, escrowAddress, data);
    EthCall response =
        requireSuccess(
            callWithFallback(web3j -> web3j.ethCall(tx, DefaultBlockParameterName.PENDING).send()),
            "isSupportedToken");
    if (response.isReverted()) {
      throw new Web3InvalidInputException(
          "isSupportedToken eth_call reverted: " + response.getRevertReason());
    }
    return Boolean.TRUE.equals(decodeBool(response.getValue()));
  }

  public void requireAdminCallable(String escrowAddress, String callerAddress) {
    String normalizedCaller = callerAddress == null ? null : callerAddress.trim();
    if (normalizedCaller == null || normalizedCaller.isBlank()) {
      throw new Web3InvalidInputException("callerAddress is required");
    }

    String ownerData =
        FunctionEncoder.encode(
            new Function("owner", List.of(), List.of(TypeReference.create(Address.class))));
    Transaction ownerRequest =
        Transaction.createEthCallTransaction(normalizedCaller, escrowAddress, ownerData);
    EthCall ownerResponse =
        requireSuccess(
            callWithFallback(
                web3j -> web3j.ethCall(ownerRequest, DefaultBlockParameterName.PENDING).send()),
            "owner");
    if (ownerResponse.isReverted()) {
      throw new Web3InvalidInputException(
          "owner eth_call reverted: " + ownerResponse.getRevertReason());
    }

    String ownerAddress = decodeAddress(ownerResponse.getValue());
    if (ownerAddress != null && normalizedCaller.equalsIgnoreCase(ownerAddress)) {
      return;
    }

    String isRelayerData =
        FunctionEncoder.encode(
            new Function(
                "isRelayer",
                List.of(new Address(normalizedCaller)),
                List.of(TypeReference.create(Bool.class))));
    Transaction isRelayerRequest =
        Transaction.createEthCallTransaction(normalizedCaller, escrowAddress, isRelayerData);
    EthCall isRelayerResponse =
        requireSuccess(
            callWithFallback(
                web3j -> web3j.ethCall(isRelayerRequest, DefaultBlockParameterName.PENDING).send()),
            "isRelayer");
    if (isRelayerResponse.isReverted()) {
      throw new Web3InvalidInputException(
          "isRelayer eth_call reverted: " + isRelayerResponse.getRevertReason());
    }
    if (!Boolean.TRUE.equals(decodeBool(isRelayerResponse.getValue()))) {
      throw new Web3InvalidInputException(
          "adminSettle caller is not relayer or owner: " + normalizedCaller);
    }
  }

  public QnaCallPrevalidationResult prevalidateContractCall(
      String fromAddress, String contractAddress, String callData) {
    Transaction callStaticRequest =
        Transaction.createEthCallTransaction(fromAddress, contractAddress, callData);
    EthCall staticCall =
        requireSuccess(
            callWithFallback(
                web3j ->
                    web3j.ethCall(callStaticRequest, DefaultBlockParameterName.PENDING).send()),
            "eth_call");
    if (staticCall.isReverted()) {
      throw new Web3InvalidInputException(
          "contract call reverted: " + staticCall.getRevertReason());
    }

    Transaction estimateRequest =
        Transaction.createFunctionCallTransaction(
            fromAddress, null, null, null, contractAddress, BigInteger.ZERO, callData);
    EthEstimateGas estimateGas =
        requireSuccess(
            callWithFallback(web3j -> web3j.ethEstimateGas(estimateRequest).send()),
            "eth_estimateGas");
    if (estimateGas.hasError()) {
      throw new Web3InvalidInputException(
          "eth_estimateGas failed: " + estimateGas.getError().getMessage());
    }

    DefaultGasFeeCalculator.FeePlan feePlan = loadFeePlan(estimateGas.getAmountUsed());
    return new QnaCallPrevalidationResult(
        feePlan.gasLimit(), feePlan.maxPriorityFeePerGas(), feePlan.maxFeePerGas());
  }

  private DefaultGasFeeCalculator.FeePlan loadFeePlan(BigInteger estimatedGas) {
    BigInteger maxPriorityFeePerGas =
        positiveOrNull(
            requireSuccess(
                    callWithFallback(web3j -> web3j.ethMaxPriorityFeePerGas().send()),
                    "eth_maxPriorityFeePerGas")
                .getMaxPriorityFeePerGas());

    BigInteger baseFee =
        positiveOrNull(
            requireSuccess(callWithFallback(web3j -> web3j.ethBaseFee().send()), "eth_baseFee")
                .getBaseFee());

    BigInteger gasPrice = null;
    if (baseFee == null) {
      gasPrice =
          positiveOrNull(
              requireSuccess(callWithFallback(web3j -> web3j.ethGasPrice().send()), "eth_gasPrice")
                  .getGasPrice());
    }

    return defaultGasFeeCalculator.calculate(
        new DefaultGasFeeCalculator.FeeInputs(
            estimatedGas, maxPriorityFeePerGas, baseFee, gasPrice));
  }

  private <T extends Response<?>> T requireSuccess(RpcAttempt<T> attempt, String operation) {
    if (attempt.response() != null && !attempt.response().hasError()) {
      return attempt.response();
    }
    throw new Web3InvalidInputException(operation + " failed");
  }

  private <T extends Response<?>> RpcAttempt<T> callWithFallback(RpcRequest<T> request) {
    RpcAttempt<T> main = call(mainWeb3j, request);
    if (main.success()) {
      return main;
    }
    RpcAttempt<T> sub = call(subWeb3j, request);
    if (sub.success()) {
      return sub;
    }
    return sub.response() != null || sub.exception() != null ? sub : main;
  }

  private <T extends Response<?>> RpcAttempt<T> call(Web3j web3j, RpcRequest<T> request) {
    try {
      return new RpcAttempt<>(request.invoke(web3j), null);
    } catch (Exception e) {
      return new RpcAttempt<>(null, e);
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private BigInteger decodeUint256(String encoded) {
    List<Type> decoded =
        FunctionReturnDecoder.decode(
            encoded, List.of((TypeReference) TypeReference.create(Uint256.class)));
    if (decoded.isEmpty()) {
      return BigInteger.ZERO;
    }
    Object value = decoded.getFirst().getValue();
    return value instanceof BigInteger bigInteger ? bigInteger : BigInteger.ZERO;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Boolean decodeBool(String encoded) {
    List<Type> decoded =
        FunctionReturnDecoder.decode(
            encoded, List.of((TypeReference) TypeReference.create(Bool.class)));
    if (decoded.isEmpty()) {
      return null;
    }
    Object value = decoded.getFirst().getValue();
    return value instanceof Boolean boolValue ? boolValue : null;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private String decodeAddress(String encoded) {
    List<Type> decoded =
        FunctionReturnDecoder.decode(
            encoded, List.of((TypeReference) TypeReference.create(Address.class)));
    if (decoded.isEmpty()) {
      return null;
    }
    Object value = decoded.getFirst().getValue();
    return value instanceof String addressValue ? addressValue : null;
  }

  private BigInteger positiveOrNull(BigInteger value) {
    return value == null || value.signum() <= 0 ? null : value;
  }

  @FunctionalInterface
  private interface RpcRequest<T extends Response<?>> {
    T invoke(Web3j web3j) throws Exception;
  }

  private record RpcAttempt<T extends Response<?>>(T response, Exception exception) {
    private boolean success() {
      return exception == null && response != null && !response.hasError();
    }
  }

  public record QnaCallPrevalidationResult(
      BigInteger gasLimit, BigInteger maxPriorityFeePerGas, BigInteger maxFeePerGas) {}
}
