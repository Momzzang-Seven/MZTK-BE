package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.web3;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigInteger;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import org.springframework.beans.factory.annotation.Value;
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
import org.web3j.protocol.http.HttpService;

@Component
@ConditionalOnAnyExecutionEnabled
@Slf4j
public class MarketplaceContractCallSupport {

  @Value("${web3.rpc.main}")
  private String mainRpcUrl;

  @Value("${web3.rpc.sub}")
  private String subRpcUrl;

  private Web3j mainWeb3j;
  private Web3j subWeb3j;

  @PostConstruct
  void init() {
    mainWeb3j = Web3j.build(new HttpService(mainRpcUrl));
    subWeb3j = Web3j.build(new HttpService(subRpcUrl));
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

  public boolean isSupportedToken(String escrowAddress, String tokenAddress) {
    String data =
        FunctionEncoder.encode(
            new Function(
                "isSupportedToken",
                List.of(new Address(tokenAddress)),
                List.of(TypeReference.create(Bool.class))));
    EthCall response = ethCall(tokenAddress, escrowAddress, data, "isSupportedToken");
    return Boolean.TRUE.equals(decodeBool(response.getValue()));
  }

  public BigInteger loadAllowance(String ownerAddress, String spenderAddress, String tokenAddress) {
    String data =
        FunctionEncoder.encode(
            new Function(
                "allowance",
                List.of(new Address(ownerAddress), new Address(spenderAddress)),
                List.of(TypeReference.create(Uint256.class))));
    EthCall response = ethCall(ownerAddress, tokenAddress, data, "allowance");
    return decodeUint256(response.getValue());
  }

  public BigInteger loadBalance(String ownerAddress, String tokenAddress) {
    String data =
        FunctionEncoder.encode(
            new Function(
                "balanceOf",
                List.of(new Address(ownerAddress)),
                List.of(TypeReference.create(Uint256.class))));
    EthCall response = ethCall(ownerAddress, tokenAddress, data, "balanceOf");
    return decodeUint256(response.getValue());
  }

  private EthCall ethCall(String fromAddress, String toAddress, String data, String operation) {
    Transaction tx = Transaction.createEthCallTransaction(fromAddress, toAddress, data);
    EthCall response =
        requireSuccess(
            callWithFallback(web3j -> web3j.ethCall(tx, DefaultBlockParameterName.PENDING).send()),
            operation);
    if (response.isReverted()) {
      throw new Web3InvalidInputException(
          operation + " eth_call reverted: " + response.getRevertReason());
    }
    return response;
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

  private String nullSafe(String text) {
    return text == null ? "" : text;
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
}
