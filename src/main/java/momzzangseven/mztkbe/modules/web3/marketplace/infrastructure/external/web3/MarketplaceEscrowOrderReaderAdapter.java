package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.web3;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceEscrowOrderPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.MarketplaceEscrowOrderView;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceEscrowIdCodec;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.StaticStruct;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint16;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint48;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.http.HttpService;

@Component
@Slf4j
@ConditionalOnAnyExecutionEnabled
public class MarketplaceEscrowOrderReaderAdapter implements LoadMarketplaceEscrowOrderPort {

  @Value("${web3.rpc.main}")
  private String mainRpcUrl;

  @Value("${web3.rpc.sub}")
  private String subRpcUrl;

  @Value("${web3.escrow.marketplace-contract-address}")
  private String marketplaceContractAddress;

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

  @Override
  public MarketplaceEscrowOrderView getOrder(String orderKey) {
    Function function =
        new Function("getOrder", List.of(bytes32(orderKey)), java.util.Collections.emptyList());
    return toOrder(orderKey, cleanHex(callAndReadValue(function, "getOrder")), 0);
  }

  @Override
  public List<MarketplaceEscrowOrderView> getOrders(List<String> orderKeys) {
    List<Bytes32> ids = orderKeys.stream().map(this::bytes32).toList();
    Function function =
        new Function(
            "getOrders",
            List.of(new DynamicArray<>(Bytes32.class, ids)),
            java.util.Collections.emptyList());
    String clean = cleanHex(callAndReadValue(function, "getOrders"));
    int offsetSlot = bigInteger(slot(clean, 0)).intValueExact() / 32;
    int orderCount = bigInteger(slot(clean, offsetSlot)).intValueExact();
    if (orderCount != orderKeys.size()) {
      throw new Web3InvalidInputException("getOrders returned mismatched order count");
    }
    int firstOrderSlot = offsetSlot + 1;
    return java.util.stream.IntStream.range(0, orderCount)
        .mapToObj(index -> toOrder(orderKeys.get(index), clean, firstOrderSlot + index * 7))
        .toList();
  }

  private Bytes32 bytes32(String orderKey) {
    return new Bytes32(MarketplaceEscrowIdCodec.orderKeyBytes(orderKey));
  }

  private String callAndReadValue(Function function, String operation) {
    String escrowAddress = marketplaceContractAddress;
    String data = FunctionEncoder.encode(function);
    Transaction tx = Transaction.createEthCallTransaction(escrowAddress, escrowAddress, data);
    EthCall response =
        requireSuccess(
            callWithFallback(web3j -> web3j.ethCall(tx, DefaultBlockParameterName.LATEST).send()),
            operation);
    if (response.isReverted()) {
      throw new Web3InvalidInputException(operation + " reverted: " + response.getRevertReason());
    }
    return response.getValue();
  }

  private MarketplaceEscrowOrderView toOrder(
      String requestedOrderKey, String clean, int startSlot) {
    return new MarketplaceEscrowOrderView(
        requestedOrderKey,
        bigInteger(slot(clean, startSlot + 1)),
        address(slot(clean, startSlot + 2)),
        bigInteger(slot(clean, startSlot + 3)).longValueExact(),
        bigInteger(slot(clean, startSlot + 4)).intValueExact(),
        address(slot(clean, startSlot + 5)),
        address(slot(clean, startSlot + 6)));
  }

  private String cleanHex(String value) {
    if (value == null || value.isBlank() || "0x".equals(value)) {
      throw new Web3InvalidInputException("marketplace order reader returned no data");
    }
    return value.startsWith("0x") ? value.substring(2) : value;
  }

  private String slot(String clean, int index) {
    int start = index * 64;
    int end = start + 64;
    if (index < 0 || clean.length() < end) {
      throw new Web3InvalidInputException("marketplace order reader returned malformed data");
    }
    return clean.substring(start, end);
  }

  private BigInteger bigInteger(String slot) {
    return new BigInteger(slot, 16);
  }

  private String address(String slot) {
    return ("0x" + slot.substring(24)).toLowerCase(Locale.ROOT);
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
      return "rpcError(code=" + error.getCode() + ", message=" + error.getMessage() + ")";
    }
    if (attempt.exception() != null) {
      return "exception("
          + attempt.exception().getClass().getSimpleName()
          + ": "
          + attempt.exception().getMessage()
          + ")";
    }
    return "noResponse";
  }

  private record RpcAttempt<T extends Response<?>>(T response, Exception exception) {
    boolean success() {
      return response != null && !response.hasError();
    }
  }

  private record RpcOutcome<T extends Response<?>>(RpcAttempt<T> main, RpcAttempt<T> sub) {
    boolean success() {
      return successAttempt() != null;
    }

    T response() {
      RpcAttempt<T> attempt = successAttempt();
      return attempt == null ? null : attempt.response();
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
  }

  @FunctionalInterface
  private interface RpcRequest<T extends Response<?>> {
    T invoke(Web3j web3j) throws Exception;
  }

  public static class ClassOrder extends StaticStruct {
    public ClassOrder(
        Bytes32 orderId,
        Uint256 price,
        Address token,
        Uint48 deadline,
        Uint16 state,
        Address buyer,
        Address trainer) {
      super(orderId, price, token, deadline, state, buyer, trainer);
    }
  }
}
