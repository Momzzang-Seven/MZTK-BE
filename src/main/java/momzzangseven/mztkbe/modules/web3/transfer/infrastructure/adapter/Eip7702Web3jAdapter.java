package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.Eip7702ChainPort;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.config.Eip7702Properties;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.config.TransferCoreProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthBaseFee;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthMaxPriorityFeePerGas;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

/** EIP-7702 chain adapter with main->sub fallback. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
public class Eip7702Web3jAdapter implements Eip7702ChainPort {

  private final TransferCoreProperties web3CoreProperties;
  private final Eip7702Properties eip7702Properties;

  private HttpService mainService;
  private HttpService subService;
  private Web3j mainWeb3j;
  private Web3j subWeb3j;

  @PostConstruct
  void init() {
    mainService = new HttpService(web3CoreProperties.getRpc().getMain());
    subService = new HttpService(web3CoreProperties.getRpc().getSub());
    mainWeb3j = Web3j.build(mainService);
    subWeb3j = Web3j.build(subService);
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
  public BigInteger loadPendingAccountNonce(String address) {
    RpcAttempt<EthGetTransactionCount> attempt =
        callWithFallback(
            web3j ->
                web3j.ethGetTransactionCount(address, DefaultBlockParameterName.PENDING).send());

    if (!attempt.success()) {
      throw new Web3InvalidInputException(
          "failed to load pending account nonce: " + attempt.errorMessage());
    }
    return attempt.response().getTransactionCount();
  }

  @Override
  public BigInteger estimateGasWithAuthorization(
      String sponsorAddress,
      String authorityAddress,
      String data,
      List<AuthorizationTuple> authList) {
    try {
      EthEstimateGas mainResult =
          estimateGas(mainService, sponsorAddress, authorityAddress, data, authList);
      if (!mainResult.hasError()) {
        return mainResult.getAmountUsed();
      }

      EthEstimateGas subResult =
          estimateGas(subService, sponsorAddress, authorityAddress, data, authList);
      if (!subResult.hasError()) {
        return subResult.getAmountUsed();
      }

      String errorMessage =
          subResult.getError() != null
              ? subResult.getError().getMessage()
              : (mainResult.getError() != null ? mainResult.getError().getMessage() : "UNKNOWN");
      throw new Web3InvalidInputException("eth_estimateGas failed: " + errorMessage);
    } catch (IOException e) {
      throw new Web3InvalidInputException(
          "eth_estimateGas failed: " + e.getClass().getSimpleName());
    }
  }

  @Override
  public FeePlan loadSponsorFeePlan() {
    BigInteger maxPriorityCap = gweiToWei(eip7702Properties.getSponsor().getMaxPriorityFeeGwei());
    BigInteger maxFeeCap = gweiToWei(eip7702Properties.getSponsor().getMaxMaxFeeGwei());

    BigInteger maxPriorityFeePerGas = maxPriorityCap;
    RpcAttempt<EthMaxPriorityFeePerGas> priorityAttempt =
        callWithFallback(web3j -> web3j.ethMaxPriorityFeePerGas().send());
    if (priorityAttempt.success()) {
      BigInteger rpcPriority = positiveOrNull(priorityAttempt.response().getMaxPriorityFeePerGas());
      if (rpcPriority != null) {
        maxPriorityFeePerGas = rpcPriority.min(maxPriorityCap);
      }
    }

    BigInteger baseFee = null;
    RpcAttempt<EthBaseFee> baseFeeAttempt = callWithFallback(web3j -> web3j.ethBaseFee().send());
    if (baseFeeAttempt.success()) {
      baseFee = positiveOrNull(baseFeeAttempt.response().getBaseFee());
    }

    if (baseFee == null) {
      RpcAttempt<EthGasPrice> gasPriceAttempt =
          callWithFallback(web3j -> web3j.ethGasPrice().send());
      if (gasPriceAttempt.success()) {
        baseFee = positiveOrNull(gasPriceAttempt.response().getGasPrice());
      }
    }

    if (baseFee == null) {
      baseFee = maxPriorityFeePerGas;
    }

    BigInteger maxFeePerGas = baseFee.multiply(BigInteger.TWO).add(maxPriorityFeePerGas);
    if (maxFeePerGas.compareTo(maxFeeCap) > 0) {
      maxFeePerGas = maxFeeCap;
    }
    if (maxFeePerGas.compareTo(maxPriorityFeePerGas) < 0) {
      maxFeePerGas = maxPriorityFeePerGas;
    }

    return new FeePlan(maxPriorityFeePerGas, maxFeePerGas);
  }

  private EthEstimateGas estimateGas(
      Web3jService web3jService,
      String sponsorAddress,
      String authorityAddress,
      String data,
      List<AuthorizationTuple> authList)
      throws IOException {
    Map<String, Object> txParam = new HashMap<>();
    txParam.put("from", sponsorAddress);
    txParam.put("to", authorityAddress);
    txParam.put("data", data);
    txParam.put("value", "0x0");
    txParam.put("authorizationList", authListToJson(authList));

    Request<?, EthEstimateGas> request =
        new Request<>("eth_estimateGas", List.of(txParam), web3jService, EthEstimateGas.class);

    return request.send();
  }

  private List<Map<String, String>> authListToJson(List<AuthorizationTuple> authList) {
    return authList.stream()
        .map(
            auth -> {
              Map<String, String> map = new HashMap<>();
              map.put("chainId", Numeric.encodeQuantity(auth.chainId()));
              map.put("address", auth.address());
              map.put("nonce", Numeric.encodeQuantity(auth.nonce()));
              map.put("yParity", Numeric.encodeQuantity(auth.yParity()));
              map.put("r", Numeric.toHexStringWithPrefixZeroPadded(auth.r(), 64));
              map.put("s", Numeric.toHexStringWithPrefixZeroPadded(auth.s(), 64));
              return map;
            })
        .toList();
  }

  private BigInteger gweiToWei(long gwei) {
    return Convert.toWei(BigInteger.valueOf(gwei).toString(), Convert.Unit.GWEI)
        .toBigIntegerExact();
  }

  private BigInteger positiveOrNull(BigInteger value) {
    if (value == null || value.signum() <= 0) {
      return null;
    }
    return value;
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

  @FunctionalInterface
  private interface RpcRequest<T extends Response<?>> {
    T invoke(Web3j web3j) throws Exception;
  }

  private record RpcAttempt<T extends Response<?>>(T response, Exception exception) {
    private boolean success() {
      return exception == null && response != null && !response.hasError();
    }

    private String errorMessage() {
      if (exception != null) {
        return exception.getClass().getSimpleName();
      }
      if (response != null && response.hasError()) {
        return response.getError().getMessage();
      }
      return "UNKNOWN";
    }
  }
}
