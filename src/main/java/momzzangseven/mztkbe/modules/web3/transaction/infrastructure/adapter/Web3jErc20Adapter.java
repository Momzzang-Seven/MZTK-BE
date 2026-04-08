package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.TransactionRewardTokenProperties;
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
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthBaseFee;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthMaxPriorityFeePerGas;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;

/** Web3ContractPort implementation using Web3j and main->sub RPC failover. */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.reward-token", name = "enabled", havingValue = "true")
public class Web3jErc20Adapter implements Web3ContractPort {

  private static final BigInteger WEI_PER_ETH = new BigInteger("1000000000000000000");

  private final TransactionRewardTokenProperties rewardTokenProperties;
  private final Web3CoreProperties web3CoreProperties;
  private final DefaultGasFeeCalculator gasFeeCalculator;

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
  public PrevalidateResult prevalidate(PrevalidateCommand command) {
    if (!isValidCommand(command)) {
      return prevalidateFailure(
          Web3TxFailureReason.PREVALIDATE_INVALID_COMMAND,
          false,
          Map.of("reason", "INVALID_COMMAND"));
    }

    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("action", "prevalidate");

    RpcAttempt<EthGetBalance> ethBalanceAttempt =
        callWithFallback(
            web3j ->
                web3j
                    .ethGetBalance(command.fromAddress(), DefaultBlockParameterName.PENDING)
                    .send());
    if (!ethBalanceAttempt.success()) {
      detail.put("ethBalanceError", ethBalanceAttempt.errorMessage());
      return prevalidateFailure(Web3TxFailureReason.RPC_UNAVAILABLE, true, detail);
    }

    BigInteger ethBalanceWei = ethBalanceAttempt.response().getBalance();
    BigInteger ethWarningWei =
        ethToWei(rewardTokenProperties.getPrevalidate().getEthWarningThreshold());
    BigInteger ethCriticalWei =
        ethToWei(rewardTokenProperties.getPrevalidate().getEthCriticalThreshold());
    detail.put("ethBalanceWei", ethBalanceWei.toString());
    detail.put("ethWarningThresholdWei", ethWarningWei.toString());
    detail.put("ethCriticalThresholdWei", ethCriticalWei.toString());
    detail.put("ethBalanceRpc", ethBalanceAttempt.alias());

    if (ethWarningWei.signum() > 0 && ethBalanceWei.compareTo(ethWarningWei) < 0) {
      detail.put("ethWarningTriggered", true);
      log.warn(
          "Treasury ETH below warning threshold: balanceWei={}, warningWei={}",
          ethBalanceWei,
          ethWarningWei);
    }

    if (ethCriticalWei.signum() > 0 && ethBalanceWei.compareTo(ethCriticalWei) < 0) {
      return prevalidateFailure(Web3TxFailureReason.TREASURY_ETH_BELOW_CRITICAL, true, detail);
    }

    String tokenContractAddress = rewardTokenProperties.getTokenContractAddress();
    String balanceOfData =
        FunctionEncoder.encode(
            new Function(
                "balanceOf",
                List.of(new Address(command.fromAddress())),
                uint256OutputParameters()));
    Transaction balanceOfCall =
        Transaction.createEthCallTransaction(
            command.fromAddress(), tokenContractAddress, balanceOfData);

    RpcAttempt<EthCall> tokenBalanceAttempt =
        callWithFallback(
            web3j -> web3j.ethCall(balanceOfCall, DefaultBlockParameterName.PENDING).send());
    if (!tokenBalanceAttempt.success()) {
      detail.put("tokenBalanceError", tokenBalanceAttempt.errorMessage());
      return prevalidateFailure(Web3TxFailureReason.RPC_UNAVAILABLE, true, detail);
    }

    EthCall tokenBalanceCall = tokenBalanceAttempt.response();
    if (tokenBalanceCall.isReverted()) {
      detail.put("revertReason", tokenBalanceCall.getRevertReason());
      return prevalidateFailure(Web3TxFailureReason.PREVALIDATE_REVERT, false, detail);
    }

    BigInteger tokenBalanceWei = decodeUint256(tokenBalanceCall.getValue());
    detail.put("tokenBalanceWei", tokenBalanceWei.toString());
    detail.put("tokenBalanceRpc", tokenBalanceAttempt.alias());

    if (tokenBalanceWei.compareTo(command.amountWei()) < 0) {
      return prevalidateFailure(Web3TxFailureReason.TREASURY_TOKEN_INSUFFICIENT, false, detail);
    }

    String transferData =
        Eip1559TransferSigner.encodeTransferData(command.toAddress(), command.amountWei());
    Transaction estimateRequest =
        Transaction.createFunctionCallTransaction(
            command.fromAddress(),
            null,
            null,
            null,
            tokenContractAddress,
            BigInteger.ZERO,
            transferData);

    Transaction callStaticRequest =
        Transaction.createEthCallTransaction(
            command.fromAddress(), tokenContractAddress, transferData);
    RpcAttempt<EthCall> callStaticAttempt =
        callWithFallback(
            web3j -> web3j.ethCall(callStaticRequest, DefaultBlockParameterName.PENDING).send());
    if (!callStaticAttempt.success()) {
      detail.put("callStaticError", callStaticAttempt.errorMessage());
      if (callStaticAttempt.response() != null && callStaticAttempt.response().hasError()) {
        return prevalidateFailure(Web3TxFailureReason.PREVALIDATE_REVERT, false, detail);
      }
      return prevalidateFailure(Web3TxFailureReason.RPC_UNAVAILABLE, true, detail);
    }
    if (callStaticAttempt.response().isReverted()) {
      detail.put("revertReason", callStaticAttempt.response().getRevertReason());
      return prevalidateFailure(Web3TxFailureReason.PREVALIDATE_REVERT, false, detail);
    }
    detail.put("callStaticRpc", callStaticAttempt.alias());
    Boolean callStaticResult = decodeBool(callStaticAttempt.response().getValue());
    detail.put("callStaticResult", callStaticResult);
    if (Boolean.FALSE.equals(callStaticResult)) {
      return prevalidateFailure(Web3TxFailureReason.PREVALIDATE_TRANSFER_FALSE, false, detail);
    }

    RpcAttempt<EthEstimateGas> estimateGasAttempt =
        callWithFallback(web3j -> web3j.ethEstimateGas(estimateRequest).send());
    if (!estimateGasAttempt.success()) {
      detail.put("estimateGasError", estimateGasAttempt.errorMessage());
      if (estimateGasAttempt.response() != null && estimateGasAttempt.response().hasError()) {
        return prevalidateFailure(Web3TxFailureReason.PREVALIDATE_REVERT, false, detail);
      }
      return prevalidateFailure(Web3TxFailureReason.RPC_UNAVAILABLE, true, detail);
    }

    DefaultGasFeeCalculator.FeePlan feePlan =
        resolveFeePlan(estimateGasAttempt.response().getAmountUsed(), detail);

    detail.put("estimatedGas", feePlan.gasLimit().toString());
    detail.put("maxPriorityFeePerGas", feePlan.maxPriorityFeePerGas().toString());
    detail.put("maxFeePerGas", feePlan.maxFeePerGas().toString());

    return new PrevalidateResult(
        true,
        false,
        null,
        feePlan.gasLimit(),
        feePlan.maxPriorityFeePerGas(),
        feePlan.maxFeePerGas(),
        Map.copyOf(detail));
  }

  @Override
  public SignedTransaction signTransfer(SignTransferCommand command) {
    return Eip1559TransferSigner.signTransfer(command);
  }

  @Override
  public BroadcastResult broadcast(BroadcastCommand command) {
    if (command == null || command.rawTx() == null || command.rawTx().isBlank()) {
      return new BroadcastResult(false, null, Web3TxFailureReason.BROADCAST_FAILED.code(), "main");
    }

    RpcAttempt<EthSendTransaction> mainAttempt =
        call("main", mainWeb3j, web3j -> web3j.ethSendRawTransaction(command.rawTx()).send());
    if (isBroadcastSuccess(mainAttempt)) {
      return new BroadcastResult(true, mainAttempt.response().getTransactionHash(), null, "main");
    }
    Web3TxFailureReason mainFailureReason = classifyBroadcastFailure(mainAttempt);

    RpcAttempt<EthSendTransaction> subAttempt =
        call("sub", subWeb3j, web3j -> web3j.ethSendRawTransaction(command.rawTx()).send());
    if (isBroadcastSuccess(subAttempt)) {
      return new BroadcastResult(true, subAttempt.response().getTransactionHash(), null, "sub");
    }
    Web3TxFailureReason subFailureReason = classifyBroadcastFailure(subAttempt);

    Web3TxFailureReason prioritizedFailure =
        prioritizeBroadcastFailure(mainFailureReason, subFailureReason);

    return new BroadcastResult(false, null, prioritizedFailure.code(), "sub");
  }

  @Override
  public ReceiptResult getReceipt(String txHash) {
    if (txHash == null || txHash.isBlank()) {
      return new ReceiptResult(
          txHash, false, null, "main", true, Web3TxFailureReason.RPC_UNAVAILABLE.code());
    }

    RpcAttempt<EthGetTransactionReceipt> mainAttempt =
        call("main", mainWeb3j, web3j -> web3j.ethGetTransactionReceipt(txHash).send());
    if (mainAttempt.success()) {
      return toReceiptResult(
          txHash, mainAttempt.alias(), mainAttempt.response().getTransactionReceipt());
    }

    RpcAttempt<EthGetTransactionReceipt> subAttempt =
        call("sub", subWeb3j, web3j -> web3j.ethGetTransactionReceipt(txHash).send());
    if (subAttempt.success()) {
      return toReceiptResult(
          txHash, subAttempt.alias(), subAttempt.response().getTransactionReceipt());
    }

    return new ReceiptResult(
        txHash, false, null, "sub", true, Web3TxFailureReason.RPC_UNAVAILABLE.code());
  }

  private boolean isValidCommand(PrevalidateCommand command) {
    return command != null
        && command.amountWei() != null
        && command.amountWei().signum() >= 0
        && command.fromAddress() != null
        && WalletUtils.isValidAddress(command.fromAddress())
        && command.toAddress() != null
        && WalletUtils.isValidAddress(command.toAddress());
  }

  private DefaultGasFeeCalculator.FeePlan resolveFeePlan(
      BigInteger estimatedGas, Map<String, Object> detail) {
    BigInteger rpcMaxPriorityFee = null;
    RpcAttempt<EthMaxPriorityFeePerGas> maxPriorityAttempt =
        callWithFallback(web3j -> web3j.ethMaxPriorityFeePerGas().send());
    if (maxPriorityAttempt.success()) {
      BigInteger value = positiveOrNull(maxPriorityAttempt.response().getMaxPriorityFeePerGas());
      detail.put("maxPriorityFeeRpc", maxPriorityAttempt.alias());
      if (value != null) {
        rpcMaxPriorityFee = value;
      }
    } else {
      detail.put("maxPriorityFeeError", maxPriorityAttempt.errorMessage());
    }

    BigInteger rpcBaseFee = null;
    BigInteger rpcGasPrice = null;
    RpcAttempt<EthBaseFee> baseFeeAttempt = callWithFallback(web3j -> web3j.ethBaseFee().send());
    if (baseFeeAttempt.success()) {
      BigInteger baseFee = positiveOrNull(baseFeeAttempt.response().getBaseFee());
      detail.put("baseFeeRpc", baseFeeAttempt.alias());
      if (baseFee != null) {
        rpcBaseFee = baseFee;
      }
    } else {
      detail.put("baseFeeError", baseFeeAttempt.errorMessage());
    }

    if (rpcBaseFee == null) {
      RpcAttempt<EthGasPrice> gasPriceAttempt =
          callWithFallback(web3j -> web3j.ethGasPrice().send());
      if (gasPriceAttempt.success()) {
        BigInteger gasPrice = positiveOrNull(gasPriceAttempt.response().getGasPrice());
        detail.put("gasPriceRpc", gasPriceAttempt.alias());
        if (gasPrice != null) {
          rpcGasPrice = gasPrice;
        }
      } else {
        detail.put("gasPriceError", gasPriceAttempt.errorMessage());
      }
    }

    DefaultGasFeeCalculator.FeePlan feePlan =
        gasFeeCalculator.calculate(
            new DefaultGasFeeCalculator.FeeInputs(
                estimatedGas, rpcMaxPriorityFee, rpcBaseFee, rpcGasPrice));

    if (rpcBaseFee == null && rpcGasPrice == null) {
      detail.put("maxFeeFallback", "strategy_default");
    }
    return feePlan;
  }

  private BigInteger decodeUint256(String encoded) {
    if (encoded == null || encoded.isBlank() || "0x".equals(encoded)) {
      return BigInteger.ZERO;
    }

    List<Type> decoded = FunctionReturnDecoder.decode(encoded, uint256DecoderOutputParameters());
    if (decoded.isEmpty()) {
      return BigInteger.ZERO;
    }
    Object value = decoded.getFirst().getValue();
    if (value instanceof BigInteger bigIntegerValue) {
      return bigIntegerValue;
    }
    return BigInteger.ZERO;
  }

  private Boolean decodeBool(String encoded) {
    if (encoded == null || encoded.isBlank() || "0x".equals(encoded)) {
      return null;
    }

    List<Type> decoded = FunctionReturnDecoder.decode(encoded, boolDecoderOutputParameters());
    if (decoded.isEmpty()) {
      return null;
    }
    Object value = decoded.getFirst().getValue();
    if (value instanceof Boolean boolValue) {
      return boolValue;
    }
    return null;
  }

  private PrevalidateResult prevalidateFailure(
      Web3TxFailureReason failureReason, boolean retryable, Map<String, Object> detail) {
    return new PrevalidateResult(
        false, retryable, failureReason.code(), null, null, null, Map.copyOf(detail));
  }

  private BigInteger ethToWei(BigDecimal ethAmount) {
    if (ethAmount == null) {
      return BigInteger.ZERO;
    }
    return ethAmount.multiply(new BigDecimal(WEI_PER_ETH)).toBigInteger();
  }

  private ReceiptResult toReceiptResult(
      String txHash, String rpcAlias, java.util.Optional<TransactionReceipt> maybeReceipt) {
    if (maybeReceipt.isEmpty()) {
      return new ReceiptResult(txHash, false, null, rpcAlias, false, null);
    }
    TransactionReceipt receipt = maybeReceipt.get();
    return new ReceiptResult(txHash, true, receipt.isStatusOK(), rpcAlias, false, null);
  }

  private <T extends Response<?>> RpcAttempt<T> callWithFallback(RpcRequest<T> request) {
    RpcAttempt<T> main = call("main", mainWeb3j, request);
    if (main.success()) {
      return main;
    }

    RpcAttempt<T> sub = call("sub", subWeb3j, request);
    if (sub.success()) {
      return sub;
    }
    return (sub.response() != null || sub.exception() != null) ? sub : main;
  }

  private <T extends Response<?>> RpcAttempt<T> call(
      String alias, Web3j client, RpcRequest<T> request) {
    try {
      return new RpcAttempt<>(alias, request.invoke(client), null);
    } catch (Exception e) {
      return new RpcAttempt<>(alias, null, e);
    }
  }

  private boolean isBroadcastSuccess(RpcAttempt<EthSendTransaction> attempt) {
    return attempt.success()
        && attempt.response() != null
        && attempt.response().getTransactionHash() != null
        && !attempt.response().getTransactionHash().isBlank();
  }

  private Web3TxFailureReason classifyBroadcastFailure(RpcAttempt<EthSendTransaction> attempt) {
    if (attempt == null) {
      return Web3TxFailureReason.BROADCAST_FAILED;
    }

    if (attempt.response() != null && attempt.response().hasError()) {
      return classifyBroadcastFailureMessage(attempt.response().getError().getMessage());
    }
    if (attempt.exception() != null) {
      return Web3TxFailureReason.RPC_UNAVAILABLE;
    }
    return Web3TxFailureReason.BROADCAST_FAILED;
  }

  static Web3TxFailureReason classifyBroadcastFailureMessage(String message) {
    if (message == null || message.isBlank()) {
      return Web3TxFailureReason.BROADCAST_FAILED;
    }

    String normalized = message.toLowerCase(Locale.ROOT);
    if (normalized.contains("insufficient funds")
        || normalized.contains("insufficient balance")
        || normalized.contains("not enough funds")) {
      return Web3TxFailureReason.TREASURY_ETH_BELOW_CRITICAL;
    }
    return Web3TxFailureReason.BROADCAST_FAILED;
  }

  private Web3TxFailureReason prioritizeBroadcastFailure(
      Web3TxFailureReason mainFailureReason, Web3TxFailureReason subFailureReason) {
    if (mainFailureReason == Web3TxFailureReason.TREASURY_ETH_BELOW_CRITICAL
        || subFailureReason == Web3TxFailureReason.TREASURY_ETH_BELOW_CRITICAL) {
      return Web3TxFailureReason.TREASURY_ETH_BELOW_CRITICAL;
    }
    if (mainFailureReason == Web3TxFailureReason.BROADCAST_FAILED
        || subFailureReason == Web3TxFailureReason.BROADCAST_FAILED) {
      return Web3TxFailureReason.BROADCAST_FAILED;
    }
    return Web3TxFailureReason.RPC_UNAVAILABLE;
  }

  private BigInteger positiveOrNull(BigInteger value) {
    if (value == null || value.signum() <= 0) {
      return null;
    }
    return value;
  }

  @FunctionalInterface
  private interface RpcRequest<T extends Response<?>> {
    T invoke(Web3j web3j) throws Exception;
  }

  private record RpcAttempt<T extends Response<?>>(String alias, T response, Exception exception) {
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

  @SuppressWarnings({"rawtypes", "unchecked"})
  private List<TypeReference<?>> uint256OutputParameters() {
    return List.of((TypeReference) TypeReference.create(Uint256.class));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private List<TypeReference<Type>> uint256DecoderOutputParameters() {
    return List.of((TypeReference) TypeReference.create(Uint256.class));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private List<TypeReference<Type>> boolDecoderOutputParameters() {
    return List.of((TypeReference) TypeReference.create(Bool.class));
  }
}
