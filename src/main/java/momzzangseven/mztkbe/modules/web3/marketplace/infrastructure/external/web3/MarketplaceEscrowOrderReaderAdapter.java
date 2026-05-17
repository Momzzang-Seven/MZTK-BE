package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.web3;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigInteger;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceEscrowOrderPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.MarketplaceEscrowOrderView;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceEscrowIdCodec;
import momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.config.MarketplaceEscrowProperties;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.StaticStruct;
import org.web3j.abi.datatypes.Type;
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
@RequiredArgsConstructor
@Slf4j
@ConditionalOnAnyExecutionEnabled
public class MarketplaceEscrowOrderReaderAdapter implements LoadMarketplaceEscrowOrderPort {

  private final Web3CoreProperties web3CoreProperties;
  private final MarketplaceEscrowProperties marketplaceEscrowProperties;

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

  @Override
  public MarketplaceEscrowOrderView getOrder(String orderKey) {
    Function function =
        new Function(
            "getOrder", List.of(bytes32(orderKey)), List.of(new TypeReference<ClassOrder>() {}));
    List<Type> decoded = callAndDecode(function, "getOrder");
    if (decoded.isEmpty()) {
      throw new Web3InvalidInputException("getOrder returned no data");
    }
    return toOrder(orderKey, (StaticStruct) decoded.get(0));
  }

  @Override
  public List<MarketplaceEscrowOrderView> getOrders(List<String> orderKeys) {
    List<Bytes32> ids = orderKeys.stream().map(this::bytes32).toList();
    Function function =
        new Function(
            "getOrders",
            List.of(new DynamicArray<>(Bytes32.class, ids)),
            List.of(new TypeReference<DynamicArray<ClassOrder>>() {}));
    List<Type> decoded = callAndDecode(function, "getOrders");
    if (decoded.isEmpty()) {
      return List.of();
    }
    @SuppressWarnings("unchecked")
    DynamicArray<ClassOrder> orders = (DynamicArray<ClassOrder>) decoded.get(0);
    List<? extends StaticStruct> values = orders.getValue();
    return java.util.stream.IntStream.range(0, values.size())
        .mapToObj(index -> toOrder(orderKeys.get(index), values.get(index)))
        .toList();
  }

  private Bytes32 bytes32(String orderKey) {
    return new Bytes32(MarketplaceEscrowIdCodec.orderKeyBytes(orderKey));
  }

  private List<Type> callAndDecode(Function function, String operation) {
    String escrowAddress = marketplaceEscrowProperties.getMarketplaceContractAddress();
    String data = FunctionEncoder.encode(function);
    Transaction tx = Transaction.createEthCallTransaction(escrowAddress, escrowAddress, data);
    EthCall response =
        requireSuccess(
            callWithFallback(web3j -> web3j.ethCall(tx, DefaultBlockParameterName.PENDING).send()),
            operation);
    if (response.isReverted()) {
      throw new Web3InvalidInputException(operation + " reverted: " + response.getRevertReason());
    }
    return FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
  }

  private MarketplaceEscrowOrderView toOrder(String requestedOrderKey, StaticStruct struct) {
    List<Type> values = struct.getValue();
    return new MarketplaceEscrowOrderView(
        requestedOrderKey,
        bigInteger(values.get(1)),
        address(values.get(2)),
        bigInteger(values.get(3)).longValueExact(),
        bigInteger(values.get(4)).intValueExact(),
        address(values.get(5)),
        address(values.get(6)));
  }

  private BigInteger bigInteger(Type value) {
    return (BigInteger) value.getValue();
  }

  private String address(Type value) {
    return ((Address) value).getValue();
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
