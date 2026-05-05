package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.web3;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigInteger;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.DefaultGasFeeCalculator;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
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
@ConditionalOnAnyExecutionEnabled
@Slf4j
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

  public void requireRelayerCallable(String escrowAddress, String callerAddress) {
    String normalizedCaller = callerAddress == null ? null : callerAddress.trim();
    if (normalizedCaller == null || normalizedCaller.isBlank()) {
      throw new Web3InvalidInputException("callerAddress is required");
    }

    if (!isRelayerRegistered(escrowAddress, normalizedCaller)) {
      throw new Web3InvalidInputException(
          "current server signer is not a registered relayer: " + normalizedCaller);
    }
  }

  public boolean isRelayerRegistered(String escrowAddress, String callerAddress) {
    String normalizedCaller = callerAddress == null ? null : callerAddress.trim();
    if (normalizedCaller == null || normalizedCaller.isBlank()) {
      throw new Web3InvalidInputException("callerAddress is required");
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
    return Boolean.TRUE.equals(decodeBool(isRelayerResponse.getValue()));
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
    BigInteger maxPriorityFeePerGas = null;
    RpcOutcome<org.web3j.protocol.core.methods.response.EthMaxPriorityFeePerGas> priorityOutcome =
        callWithFallback(web3j -> web3j.ethMaxPriorityFeePerGas().send());
    if (priorityOutcome.success()) {
      maxPriorityFeePerGas = positiveOrNull(priorityOutcome.response().getMaxPriorityFeePerGas());
    }

    BigInteger baseFee = null;
    RpcOutcome<org.web3j.protocol.core.methods.response.EthBaseFee> baseFeeOutcome =
        callWithFallback(web3j -> web3j.ethBaseFee().send());
    if (baseFeeOutcome.success()) {
      baseFee = positiveOrNull(baseFeeOutcome.response().getBaseFee());
    }

    BigInteger gasPrice = null;
    if (baseFee == null) {
      RpcOutcome<org.web3j.protocol.core.methods.response.EthGasPrice> gasPriceOutcome =
          callWithFallback(web3j -> web3j.ethGasPrice().send());
      if (gasPriceOutcome.success()) {
        gasPrice = positiveOrNull(gasPriceOutcome.response().getGasPrice());
      }
    }

    return defaultGasFeeCalculator.calculate(
        new DefaultGasFeeCalculator.FeeInputs(
            estimatedGas, maxPriorityFeePerGas, baseFee, gasPrice));
  }

  private <T extends Response<?>> T requireSuccess(RpcOutcome<T> outcome, String operation) {
    if (outcome.success()) {
      return outcome.response();
    }
    String detail = describeFailure(outcome);
    log.warn("{} failed; {}", operation, detail);
    throw new Web3InvalidInputException(operation + " failed: " + detail);
  }

  private <T extends Response<?>> RpcOutcome<T> callWithFallback(RpcRequest<T> request) {
    RpcAttempt<T> main = call(mainWeb3j, request);
    if (main.success()) {
      return new RpcOutcome<>(main, null);
    }
    RpcAttempt<T> sub = call(subWeb3j, request);
    return new RpcOutcome<>(main, sub);
  }

  private <T extends Response<?>> RpcAttempt<T> call(Web3j web3j, RpcRequest<T> request) {
    try {
      return new RpcAttempt<>(request.invoke(web3j), null);
    } catch (Exception e) {
      return new RpcAttempt<>(null, e);
    }
  }

  private String describeFailure(RpcOutcome<?> outcome) {
    return "main=" + describeAttempt(outcome.main()) + ", sub=" + describeAttempt(outcome.sub());
  }

  private String describeAttempt(RpcAttempt<?> attempt) {
    if (attempt == null) {
      return "skipped";
    }
    if (attempt.response() != null && attempt.response().hasError()) {
      Response.Error error = attempt.response().getError();
      String data = error.getData() == null ? "" : error.getData();
      return "rpcError(code="
          + error.getCode()
          + ", message="
          + nullSafe(error.getMessage())
          + (data.isEmpty() ? "" : ", data=" + data)
          + ")";
    }
    if (attempt.exception() != null) {
      return "exception("
          + attempt.exception().getClass().getSimpleName()
          + ": "
          + nullSafe(attempt.exception().getMessage())
          + ")";
    }
    return "noResponse";
  }

  private String nullSafe(String text) {
    return text == null ? "" : text;
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

  private record RpcOutcome<T extends Response<?>>(RpcAttempt<T> main, RpcAttempt<T> sub) {
    private boolean success() {
      return successAttempt() != null;
    }

    private RpcAttempt<T> successAttempt() {
      if (main != null && main.success()) {
        return main;
      }
      if (sub != null && sub.success()) {
        return sub;
      }
      return null;
    }

    private T response() {
      RpcAttempt<T> attempt = successAttempt();
      return attempt == null ? null : attempt.response();
    }
  }

  public record QnaCallPrevalidationResult(
      BigInteger gasLimit, BigInteger maxPriorityFeePerGas, BigInteger maxFeePerGas) {}
}
