package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.GasFeeStrategy;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.config.TransferCoreProperties;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.config.TransferRewardTokenProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;
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
import org.web3j.utils.Numeric;

@SuppressWarnings("unchecked")
class Web3jErc20AdapterTest {

  private static final String FROM = "0x" + "a".repeat(40);
  private static final String TO = "0x" + "b".repeat(40);
  private static final String TOKEN_CONTRACT = "0x" + "c".repeat(40);
  private static final String TX_HASH = "0x" + "d".repeat(64);

  private TransferRewardTokenProperties rewardTokenProperties;
  private GasFeeStrategy gasFeeStrategy;
  private Web3j mainWeb3j;
  private Web3j subWeb3j;
  private Web3jErc20Adapter adapter;

  @BeforeEach
  void setUp() {
    rewardTokenProperties = new TransferRewardTokenProperties();
    rewardTokenProperties.setTokenContractAddress(TOKEN_CONTRACT);
    rewardTokenProperties.getPrevalidate().setEthWarningThreshold(new BigDecimal("0.5"));
    rewardTokenProperties.getPrevalidate().setEthCriticalThreshold(new BigDecimal("0.1"));

    TransferCoreProperties coreProperties = new TransferCoreProperties();
    coreProperties.getRpc().setMain("http://localhost:8545");
    coreProperties.getRpc().setSub("http://localhost:8546");

    gasFeeStrategy = mock(GasFeeStrategy.class);
    adapter = new Web3jErc20Adapter(rewardTokenProperties, coreProperties, gasFeeStrategy);

    mainWeb3j = mock(Web3j.class, RETURNS_DEEP_STUBS);
    subWeb3j = mock(Web3j.class, RETURNS_DEEP_STUBS);
    ReflectionTestUtils.setField(adapter, "mainWeb3j", mainWeb3j);
    ReflectionTestUtils.setField(adapter, "subWeb3j", subWeb3j);
  }

  @Test
  void prevalidate_returnsInvalidCommand_whenCommandIsNull() {
    Web3ContractPort.PrevalidateResult result = adapter.prevalidate(null);

    assertThat(result.ok()).isFalse();
    assertThat(result.retryable()).isFalse();
    assertThat(result.failureReason()).isEqualTo(Web3TxFailureReason.PREVALIDATE_INVALID_COMMAND.code());
    assertThat(result.detail()).containsEntry("reason", "INVALID_COMMAND");
  }

  @Test
  void prevalidate_returnsInvalidCommand_whenWalletAddressValidationFails() {
    Web3ContractPort.PrevalidateCommand command = command(BigInteger.ONE);

    try (MockedStatic<WalletUtils> walletUtils = mockStatic(WalletUtils.class, CALLS_REAL_METHODS)) {
      walletUtils.when(() -> WalletUtils.isValidAddress(FROM)).thenReturn(false);
      walletUtils.when(() -> WalletUtils.isValidAddress(TO)).thenReturn(false);

      Web3ContractPort.PrevalidateResult result = adapter.prevalidate(command);

      assertThat(result.ok()).isFalse();
      assertThat(result.retryable()).isFalse();
      assertThat(result.failureReason())
          .isEqualTo(Web3TxFailureReason.PREVALIDATE_INVALID_COMMAND.code());
    }
  }

  @Test
  void prevalidate_returnsInvalidCommand_whenAmountIsNull() {
    Web3ContractPort.PrevalidateCommand command = mock(Web3ContractPort.PrevalidateCommand.class);
    when(command.amountWei()).thenReturn(null);
    when(command.fromAddress()).thenReturn(FROM);
    when(command.toAddress()).thenReturn(TO);

    Web3ContractPort.PrevalidateResult result = adapter.prevalidate(command);

    assertThat(result.ok()).isFalse();
    assertThat(result.failureReason())
        .isEqualTo(Web3TxFailureReason.PREVALIDATE_INVALID_COMMAND.code());
  }

  @Test
  void prevalidate_returnsInvalidCommand_whenAmountIsNegative() {
    Web3ContractPort.PrevalidateCommand command = mock(Web3ContractPort.PrevalidateCommand.class);
    when(command.amountWei()).thenReturn(BigInteger.valueOf(-1));
    when(command.fromAddress()).thenReturn(FROM);
    when(command.toAddress()).thenReturn(TO);

    Web3ContractPort.PrevalidateResult result = adapter.prevalidate(command);

    assertThat(result.ok()).isFalse();
    assertThat(result.failureReason())
        .isEqualTo(Web3TxFailureReason.PREVALIDATE_INVALID_COMMAND.code());
  }

  @Test
  void prevalidate_returnsInvalidCommand_whenFromAddressIsNull() {
    Web3ContractPort.PrevalidateCommand command = mock(Web3ContractPort.PrevalidateCommand.class);
    when(command.amountWei()).thenReturn(BigInteger.ONE);
    when(command.fromAddress()).thenReturn(null);
    when(command.toAddress()).thenReturn(TO);

    Web3ContractPort.PrevalidateResult result = adapter.prevalidate(command);

    assertThat(result.ok()).isFalse();
    assertThat(result.failureReason())
        .isEqualTo(Web3TxFailureReason.PREVALIDATE_INVALID_COMMAND.code());
  }

  @Test
  void prevalidate_returnsInvalidCommand_whenToAddressIsNull() {
    Web3ContractPort.PrevalidateCommand command = mock(Web3ContractPort.PrevalidateCommand.class);
    when(command.amountWei()).thenReturn(BigInteger.ONE);
    when(command.fromAddress()).thenReturn(FROM);
    when(command.toAddress()).thenReturn(null);

    Web3ContractPort.PrevalidateResult result = adapter.prevalidate(command);

    assertThat(result.ok()).isFalse();
    assertThat(result.failureReason())
        .isEqualTo(Web3TxFailureReason.PREVALIDATE_INVALID_COMMAND.code());
  }

  @Test
  void prevalidate_returnsInvalidCommand_whenToAddressValidationFails() {
    Web3ContractPort.PrevalidateCommand command = command(BigInteger.ONE);

    try (MockedStatic<WalletUtils> walletUtils = mockStatic(WalletUtils.class, CALLS_REAL_METHODS)) {
      walletUtils.when(() -> WalletUtils.isValidAddress(FROM)).thenReturn(true);
      walletUtils.when(() -> WalletUtils.isValidAddress(TO)).thenReturn(false);

      Web3ContractPort.PrevalidateResult result = adapter.prevalidate(command);

      assertThat(result.ok()).isFalse();
      assertThat(result.failureReason())
          .isEqualTo(Web3TxFailureReason.PREVALIDATE_INVALID_COMMAND.code());
    }
  }

  @Test
  void prevalidate_returnsRpcUnavailable_whenEthBalanceRpcFailsBothMainAndSub() throws Exception {
    Web3ContractPort.PrevalidateCommand command = command(BigInteger.ONE);

    when(mainWeb3j.ethGetBalance(FROM, DefaultBlockParameterName.PENDING).send())
        .thenThrow(new IOException("main down"));
    when(subWeb3j.ethGetBalance(FROM, DefaultBlockParameterName.PENDING).send())
        .thenThrow(new IOException("sub down"));

    Web3ContractPort.PrevalidateResult result = adapter.prevalidate(command);

    assertThat(result.ok()).isFalse();
    assertThat(result.retryable()).isTrue();
    assertThat(result.failureReason()).isEqualTo(Web3TxFailureReason.RPC_UNAVAILABLE.code());
    assertThat(result.detail()).containsKey("ethBalanceError");
  }

  @Test
  void prevalidate_usesSubBalanceRpc_whenMainBalanceHasError() throws Exception {
    EthGetBalance mainBalanceError = new EthGetBalance();
    mainBalanceError.setError(new Response.Error(1, "main-balance-error"));
    when(mainWeb3j.ethGetBalance(FROM, DefaultBlockParameterName.PENDING).send())
        .thenReturn(mainBalanceError);
    when(subWeb3j.ethGetBalance(FROM, DefaultBlockParameterName.PENDING).send())
        .thenReturn(ethBalance(eth("10")));

    when(mainWeb3j.ethCall(any(Transaction.class), eq(DefaultBlockParameterName.PENDING)).send())
        .thenReturn(ethCallUint256(BigInteger.valueOf(100)), ethCallBool(true));
    when(mainWeb3j.ethEstimateGas(any(Transaction.class)).send())
        .thenReturn(ethEstimateGas(BigInteger.valueOf(120_000)));

    EthMaxPriorityFeePerGas maxPriorityFee = new EthMaxPriorityFeePerGas();
    maxPriorityFee.setResult(Numeric.encodeQuantity(BigInteger.valueOf(3_000_000_000L)));
    EthBaseFee baseFee = new EthBaseFee();
    baseFee.setResult(Numeric.encodeQuantity(BigInteger.valueOf(2_000_000_000L)));
    when(mainWeb3j.ethMaxPriorityFeePerGas().send()).thenReturn(maxPriorityFee);
    when(mainWeb3j.ethBaseFee().send()).thenReturn(baseFee);
    when(gasFeeStrategy.calculate(any()))
        .thenReturn(
            new GasFeeStrategy.FeePlan(
                BigInteger.valueOf(210_000),
                BigInteger.valueOf(1_500_000_000L),
                BigInteger.valueOf(4_500_000_000L)));

    Web3ContractPort.PrevalidateResult result = adapter.prevalidate(command(BigInteger.TWO));

    assertThat(result.ok()).isTrue();
    assertThat(result.detail()).containsEntry("ethBalanceRpc", "sub");
  }

  @Test
  void prevalidate_usesMainError_whenSubBalanceResponseIsNull() throws Exception {
    Web3ContractPort.PrevalidateCommand command = command(BigInteger.ONE);

    EthGetBalance mainError = new EthGetBalance();
    mainError.setError(new Response.Error(1, "main-balance-error"));
    when(mainWeb3j.ethGetBalance(FROM, DefaultBlockParameterName.PENDING).send())
        .thenReturn(mainError);
    when(subWeb3j.ethGetBalance(FROM, DefaultBlockParameterName.PENDING).send()).thenReturn(null);

    Web3ContractPort.PrevalidateResult result = adapter.prevalidate(command);

    assertThat(result.ok()).isFalse();
    assertThat(result.failureReason()).isEqualTo(Web3TxFailureReason.RPC_UNAVAILABLE.code());
    assertThat(result.detail()).containsEntry("ethBalanceError", "main-balance-error");
  }

  @Test
  void prevalidate_returnsCritical_whenEthBalanceBelowCriticalThreshold() throws Exception {
    rewardTokenProperties.getPrevalidate().setEthWarningThreshold(new BigDecimal("2"));
    rewardTokenProperties.getPrevalidate().setEthCriticalThreshold(new BigDecimal("3"));

    when(mainWeb3j.ethGetBalance(FROM, DefaultBlockParameterName.PENDING).send())
        .thenReturn(ethBalance(eth("1")));

    Web3ContractPort.PrevalidateResult result = adapter.prevalidate(command(BigInteger.ONE));

    assertThat(result.ok()).isFalse();
    assertThat(result.retryable()).isTrue();
    assertThat(result.failureReason())
        .isEqualTo(Web3TxFailureReason.TREASURY_ETH_BELOW_CRITICAL.code());
    assertThat(result.detail()).containsEntry("ethWarningTriggered", true);
  }

  @Test
  void prevalidate_returnsRpcUnavailable_whenTokenBalanceRpcFails() throws Exception {
    when(mainWeb3j.ethGetBalance(FROM, DefaultBlockParameterName.PENDING).send())
        .thenReturn(ethBalance(eth("10")));
    when(mainWeb3j.ethCall(any(Transaction.class), eq(DefaultBlockParameterName.PENDING)).send())
        .thenThrow(new IOException("main call fail"));
    when(subWeb3j.ethCall(any(Transaction.class), eq(DefaultBlockParameterName.PENDING)).send())
        .thenThrow(new IOException("sub call fail"));

    Web3ContractPort.PrevalidateResult result = adapter.prevalidate(command(BigInteger.ONE));

    assertThat(result.ok()).isFalse();
    assertThat(result.retryable()).isTrue();
    assertThat(result.failureReason()).isEqualTo(Web3TxFailureReason.RPC_UNAVAILABLE.code());
    assertThat(result.detail()).containsKey("tokenBalanceError");
  }

  @Test
  void prevalidate_returnsRevert_whenTokenBalanceCallReverted() throws Exception {
    when(mainWeb3j.ethGetBalance(FROM, DefaultBlockParameterName.PENDING).send())
        .thenReturn(ethBalance(eth("10")));

    EthCall revertedTokenBalance = mock(EthCall.class);
    when(revertedTokenBalance.hasError()).thenReturn(false);
    when(revertedTokenBalance.isReverted()).thenReturn(true);
    when(revertedTokenBalance.getRevertReason()).thenReturn("token revert");
    when(mainWeb3j.ethCall(any(Transaction.class), eq(DefaultBlockParameterName.PENDING)).send())
        .thenReturn(revertedTokenBalance);

    Web3ContractPort.PrevalidateResult result = adapter.prevalidate(command(BigInteger.ONE));

    assertThat(result.ok()).isFalse();
    assertThat(result.retryable()).isFalse();
    assertThat(result.failureReason()).isEqualTo(Web3TxFailureReason.PREVALIDATE_REVERT.code());
    assertThat(result.detail()).containsEntry("revertReason", "token revert");
  }

  @Test
  void prevalidate_returnsTokenInsufficient_whenTreasuryBalanceLessThanAmount() throws Exception {
    when(mainWeb3j.ethGetBalance(FROM, DefaultBlockParameterName.PENDING).send())
        .thenReturn(ethBalance(eth("10")));
    when(mainWeb3j.ethCall(any(Transaction.class), eq(DefaultBlockParameterName.PENDING)).send())
        .thenReturn(ethCallUint256(BigInteger.ONE));

    Web3ContractPort.PrevalidateResult result = adapter.prevalidate(command(BigInteger.TWO));

    assertThat(result.ok()).isFalse();
    assertThat(result.retryable()).isFalse();
    assertThat(result.failureReason())
        .isEqualTo(Web3TxFailureReason.TREASURY_TOKEN_INSUFFICIENT.code());
  }

  @Test
  void prevalidate_returnsRevert_whenCallStaticAttemptHasErrorResponse() throws Exception {
    when(mainWeb3j.ethGetBalance(FROM, DefaultBlockParameterName.PENDING).send())
        .thenReturn(ethBalance(eth("10")));

    EthCall mainCallStaticError = new EthCall();
    mainCallStaticError.setError(new Response.Error(1, "main-static-error"));
    EthCall subCallStaticError = new EthCall();
    subCallStaticError.setError(new Response.Error(2, "sub-static-error"));

    when(mainWeb3j.ethCall(any(Transaction.class), eq(DefaultBlockParameterName.PENDING)).send())
        .thenReturn(ethCallUint256(BigInteger.valueOf(100)), mainCallStaticError);
    when(subWeb3j.ethCall(any(Transaction.class), eq(DefaultBlockParameterName.PENDING)).send())
        .thenReturn(subCallStaticError);

    Web3ContractPort.PrevalidateResult result = adapter.prevalidate(command(BigInteger.TWO));

    assertThat(result.ok()).isFalse();
    assertThat(result.retryable()).isFalse();
    assertThat(result.failureReason()).isEqualTo(Web3TxFailureReason.PREVALIDATE_REVERT.code());
    assertThat(result.detail()).containsKey("callStaticError");
  }

  @Test
  void prevalidate_returnsRpcUnavailable_whenCallStaticErrorCheckFallsThrough() throws Exception {
    when(mainWeb3j.ethGetBalance(FROM, DefaultBlockParameterName.PENDING).send())
        .thenReturn(ethBalance(eth("10")));

    EthCall subCallStaticErrorThenFalse = mock(EthCall.class);
    when(subCallStaticErrorThenFalse.hasError()).thenReturn(true, true, false);

    when(mainWeb3j.ethCall(any(Transaction.class), eq(DefaultBlockParameterName.PENDING)).send())
        .thenReturn(ethCallUint256(BigInteger.valueOf(100)))
        .thenThrow(new IOException("main static down"));
    when(subWeb3j.ethCall(any(Transaction.class), eq(DefaultBlockParameterName.PENDING)).send())
        .thenReturn(subCallStaticErrorThenFalse);

    Web3ContractPort.PrevalidateResult result = adapter.prevalidate(command(BigInteger.TWO));

    assertThat(result.ok()).isFalse();
    assertThat(result.retryable()).isTrue();
    assertThat(result.failureReason()).isEqualTo(Web3TxFailureReason.RPC_UNAVAILABLE.code());
  }

  @Test
  void prevalidate_returnsRpcUnavailable_whenCallStaticAttemptThrows() throws Exception {
    when(mainWeb3j.ethGetBalance(FROM, DefaultBlockParameterName.PENDING).send())
        .thenReturn(ethBalance(eth("10")));
    when(mainWeb3j.ethCall(any(Transaction.class), eq(DefaultBlockParameterName.PENDING)).send())
        .thenReturn(ethCallUint256(BigInteger.valueOf(100)))
        .thenThrow(new IOException("main static down"));
    when(subWeb3j.ethCall(any(Transaction.class), eq(DefaultBlockParameterName.PENDING)).send())
        .thenThrow(new IOException("sub static down"));

    Web3ContractPort.PrevalidateResult result = adapter.prevalidate(command(BigInteger.TWO));

    assertThat(result.ok()).isFalse();
    assertThat(result.retryable()).isTrue();
    assertThat(result.failureReason()).isEqualTo(Web3TxFailureReason.RPC_UNAVAILABLE.code());
  }

  @Test
  void prevalidate_returnsRevert_whenCallStaticReverted() throws Exception {
    when(mainWeb3j.ethGetBalance(FROM, DefaultBlockParameterName.PENDING).send())
        .thenReturn(ethBalance(eth("10")));

    EthCall callStaticReverted = mock(EthCall.class);
    when(callStaticReverted.hasError()).thenReturn(false);
    when(callStaticReverted.isReverted()).thenReturn(true);
    when(callStaticReverted.getRevertReason()).thenReturn("static revert");

    when(mainWeb3j.ethCall(any(Transaction.class), eq(DefaultBlockParameterName.PENDING)).send())
        .thenReturn(ethCallUint256(BigInteger.valueOf(100)), callStaticReverted);

    Web3ContractPort.PrevalidateResult result = adapter.prevalidate(command(BigInteger.TWO));

    assertThat(result.ok()).isFalse();
    assertThat(result.retryable()).isFalse();
    assertThat(result.failureReason()).isEqualTo(Web3TxFailureReason.PREVALIDATE_REVERT.code());
    assertThat(result.detail()).containsEntry("revertReason", "static revert");
  }

  @Test
  void prevalidate_returnsTransferFalse_whenCallStaticReturnsFalse() throws Exception {
    when(mainWeb3j.ethGetBalance(FROM, DefaultBlockParameterName.PENDING).send())
        .thenReturn(ethBalance(eth("10")));
    when(mainWeb3j.ethCall(any(Transaction.class), eq(DefaultBlockParameterName.PENDING)).send())
        .thenReturn(ethCallUint256(BigInteger.valueOf(100)), ethCallBool(false));

    Web3ContractPort.PrevalidateResult result = adapter.prevalidate(command(BigInteger.TWO));

    assertThat(result.ok()).isFalse();
    assertThat(result.retryable()).isFalse();
    assertThat(result.failureReason())
        .isEqualTo(Web3TxFailureReason.PREVALIDATE_TRANSFER_FALSE.code());
  }

  @Test
  void prevalidate_returnsRevert_whenEstimateGasReturnsError() throws Exception {
    when(mainWeb3j.ethGetBalance(FROM, DefaultBlockParameterName.PENDING).send())
        .thenReturn(ethBalance(eth("10")));
    when(mainWeb3j.ethCall(any(Transaction.class), eq(DefaultBlockParameterName.PENDING)).send())
        .thenReturn(ethCallUint256(BigInteger.valueOf(100)), ethCallBool(true));

    EthEstimateGas mainEstimateError = new EthEstimateGas();
    mainEstimateError.setError(new Response.Error(1, "estimate-main-error"));
    EthEstimateGas subEstimateError = new EthEstimateGas();
    subEstimateError.setError(new Response.Error(2, "estimate-sub-error"));
    when(mainWeb3j.ethEstimateGas(any(Transaction.class)).send()).thenReturn(mainEstimateError);
    when(subWeb3j.ethEstimateGas(any(Transaction.class)).send()).thenReturn(subEstimateError);

    Web3ContractPort.PrevalidateResult result = adapter.prevalidate(command(BigInteger.TWO));

    assertThat(result.ok()).isFalse();
    assertThat(result.retryable()).isFalse();
    assertThat(result.failureReason()).isEqualTo(Web3TxFailureReason.PREVALIDATE_REVERT.code());
  }

  @Test
  void prevalidate_returnsRpcUnavailable_whenEstimateGasThrows() throws Exception {
    when(mainWeb3j.ethGetBalance(FROM, DefaultBlockParameterName.PENDING).send())
        .thenReturn(ethBalance(eth("10")));
    when(mainWeb3j.ethCall(any(Transaction.class), eq(DefaultBlockParameterName.PENDING)).send())
        .thenReturn(ethCallUint256(BigInteger.valueOf(100)), ethCallBool(true));
    when(mainWeb3j.ethEstimateGas(any(Transaction.class)).send())
        .thenThrow(new IOException("estimate-main-down"));
    when(subWeb3j.ethEstimateGas(any(Transaction.class)).send())
        .thenThrow(new IOException("estimate-sub-down"));

    Web3ContractPort.PrevalidateResult result = adapter.prevalidate(command(BigInteger.TWO));

    assertThat(result.ok()).isFalse();
    assertThat(result.retryable()).isTrue();
    assertThat(result.failureReason()).isEqualTo(Web3TxFailureReason.RPC_UNAVAILABLE.code());
  }

  @Test
  void prevalidate_returnsRpcUnavailable_whenEstimateGasErrorCheckFallsThrough() throws Exception {
    when(mainWeb3j.ethGetBalance(FROM, DefaultBlockParameterName.PENDING).send())
        .thenReturn(ethBalance(eth("10")));
    when(mainWeb3j.ethCall(any(Transaction.class), eq(DefaultBlockParameterName.PENDING)).send())
        .thenReturn(ethCallUint256(BigInteger.valueOf(100)), ethCallBool(true));

    EthEstimateGas subEstimateErrorThenFalse = mock(EthEstimateGas.class);
    when(subEstimateErrorThenFalse.hasError()).thenReturn(true, true, false);

    when(mainWeb3j.ethEstimateGas(any(Transaction.class)).send())
        .thenThrow(new IOException("estimate-main-down"));
    when(subWeb3j.ethEstimateGas(any(Transaction.class)).send())
        .thenReturn(subEstimateErrorThenFalse);

    Web3ContractPort.PrevalidateResult result = adapter.prevalidate(command(BigInteger.TWO));

    assertThat(result.ok()).isFalse();
    assertThat(result.retryable()).isTrue();
    assertThat(result.failureReason()).isEqualTo(Web3TxFailureReason.RPC_UNAVAILABLE.code());
  }

  @Test
  void prevalidate_returnsSuccessAndUsesFeeInputs_whenAllChecksPass() throws Exception {
    when(mainWeb3j.ethGetBalance(FROM, DefaultBlockParameterName.PENDING).send())
        .thenReturn(ethBalance(eth("10")));
    when(mainWeb3j.ethCall(any(Transaction.class), eq(DefaultBlockParameterName.PENDING)).send())
        .thenReturn(ethCallUint256(BigInteger.valueOf(100)), ethCallBool(true));
    when(mainWeb3j.ethEstimateGas(any(Transaction.class)).send())
        .thenReturn(ethEstimateGas(BigInteger.valueOf(120_000)));

    EthMaxPriorityFeePerGas maxPriorityFee = new EthMaxPriorityFeePerGas();
    maxPriorityFee.setResult(Numeric.encodeQuantity(BigInteger.valueOf(3_000_000_000L)));
    EthBaseFee baseFee = new EthBaseFee();
    baseFee.setResult(Numeric.encodeQuantity(BigInteger.valueOf(2_000_000_000L)));
    when(mainWeb3j.ethMaxPriorityFeePerGas().send()).thenReturn(maxPriorityFee);
    when(mainWeb3j.ethBaseFee().send()).thenReturn(baseFee);

    GasFeeStrategy.FeePlan plan =
        new GasFeeStrategy.FeePlan(
            BigInteger.valueOf(210_000),
            BigInteger.valueOf(1_500_000_000L),
            BigInteger.valueOf(4_500_000_000L));
    when(gasFeeStrategy.calculate(any())).thenReturn(plan);

    Web3ContractPort.PrevalidateResult result = adapter.prevalidate(command(BigInteger.TWO));

    assertThat(result.ok()).isTrue();
    assertThat(result.retryable()).isFalse();
    assertThat(result.failureReason()).isNull();
    assertThat(result.gasLimit()).isEqualTo(plan.gasLimit());
    assertThat(result.maxPriorityFeePerGas()).isEqualTo(plan.maxPriorityFeePerGas());
    assertThat(result.maxFeePerGas()).isEqualTo(plan.maxFeePerGas());
    assertThat(result.detail()).containsEntry("action", "prevalidate");
    assertThat(result.detail()).containsEntry("tokenBalanceRpc", "main");

    ArgumentCaptor<GasFeeStrategy.FeeInputs> captor =
        ArgumentCaptor.forClass(GasFeeStrategy.FeeInputs.class);
    verify(gasFeeStrategy).calculate(captor.capture());
    GasFeeStrategy.FeeInputs inputs = captor.getValue();
    assertThat(inputs.estimatedGas()).isEqualTo(BigInteger.valueOf(120_000));
    assertThat(inputs.maxPriorityFeePerGas()).isEqualTo(BigInteger.valueOf(3_000_000_000L));
    assertThat(inputs.baseFeePerGas()).isEqualTo(BigInteger.valueOf(2_000_000_000L));
    assertThat(inputs.gasPrice()).isNull();
  }

  @Test
  void prevalidate_marksFallbackDetail_whenBaseFeeAndGasPriceUnavailable() throws Exception {
    rewardTokenProperties.getPrevalidate().setEthWarningThreshold(null);
    rewardTokenProperties.getPrevalidate().setEthCriticalThreshold(null);

    when(mainWeb3j.ethGetBalance(FROM, DefaultBlockParameterName.PENDING).send())
        .thenReturn(ethBalance(eth("10")));
    when(mainWeb3j.ethCall(any(Transaction.class), eq(DefaultBlockParameterName.PENDING)).send())
        .thenReturn(ethCallUint256(BigInteger.valueOf(100)), ethCallBool(true));
    when(mainWeb3j.ethEstimateGas(any(Transaction.class)).send())
        .thenReturn(ethEstimateGas(BigInteger.valueOf(120_000)));

    EthMaxPriorityFeePerGas mainPriorityError = new EthMaxPriorityFeePerGas();
    mainPriorityError.setError(new Response.Error(1, "priority-main-error"));
    EthMaxPriorityFeePerGas subPriorityError = new EthMaxPriorityFeePerGas();
    subPriorityError.setError(new Response.Error(2, "priority-sub-error"));
    when(mainWeb3j.ethMaxPriorityFeePerGas().send()).thenReturn(mainPriorityError);
    when(subWeb3j.ethMaxPriorityFeePerGas().send()).thenReturn(subPriorityError);

    EthBaseFee mainBaseError = new EthBaseFee();
    mainBaseError.setError(new Response.Error(3, "base-main-error"));
    EthBaseFee subBaseError = new EthBaseFee();
    subBaseError.setError(new Response.Error(4, "base-sub-error"));
    when(mainWeb3j.ethBaseFee().send()).thenReturn(mainBaseError);
    when(subWeb3j.ethBaseFee().send()).thenReturn(subBaseError);

    EthGasPrice mainGasError = new EthGasPrice();
    mainGasError.setError(new Response.Error(5, "gas-main-error"));
    EthGasPrice subGasError = new EthGasPrice();
    subGasError.setError(new Response.Error(6, "gas-sub-error"));
    when(mainWeb3j.ethGasPrice().send()).thenReturn(mainGasError);
    when(subWeb3j.ethGasPrice().send()).thenReturn(subGasError);

    GasFeeStrategy.FeePlan plan =
        new GasFeeStrategy.FeePlan(
            BigInteger.valueOf(200_000),
            BigInteger.valueOf(1_000_000_000L),
            BigInteger.valueOf(2_000_000_000L));
    when(gasFeeStrategy.calculate(any())).thenReturn(plan);

    Web3ContractPort.PrevalidateResult result = adapter.prevalidate(command(BigInteger.TWO));

    assertThat(result.ok()).isTrue();
    assertThat(result.detail()).containsEntry("maxFeeFallback", "strategy_default");
    assertThat(result.detail()).containsKeys("maxPriorityFeeError", "baseFeeError", "gasPriceError");
  }

  @Test
  void prevalidate_resolveFeePlan_usesGasPriceWhenBaseFeeIsNonPositive() throws Exception {
    when(mainWeb3j.ethGetBalance(FROM, DefaultBlockParameterName.PENDING).send())
        .thenReturn(ethBalance(eth("10")));
    when(mainWeb3j.ethCall(any(Transaction.class), eq(DefaultBlockParameterName.PENDING)).send())
        .thenReturn(ethCallUint256(BigInteger.valueOf(100)), ethCallBool(true));
    when(mainWeb3j.ethEstimateGas(any(Transaction.class)).send())
        .thenReturn(ethEstimateGas(BigInteger.valueOf(120_000)));

    EthMaxPriorityFeePerGas priorityZero = new EthMaxPriorityFeePerGas();
    priorityZero.setResult("0x0");
    when(mainWeb3j.ethMaxPriorityFeePerGas().send()).thenReturn(priorityZero);

    EthBaseFee baseFeeZero = new EthBaseFee();
    baseFeeZero.setResult("0x0");
    when(mainWeb3j.ethBaseFee().send()).thenReturn(baseFeeZero);

    EthGasPrice gasPricePositive = new EthGasPrice();
    gasPricePositive.setResult(Numeric.encodeQuantity(BigInteger.valueOf(2_000_000_000L)));
    when(mainWeb3j.ethGasPrice().send()).thenReturn(gasPricePositive);

    GasFeeStrategy.FeePlan plan =
        new GasFeeStrategy.FeePlan(
            BigInteger.valueOf(200_000),
            BigInteger.valueOf(1_000_000_000L),
            BigInteger.valueOf(2_000_000_000L));
    when(gasFeeStrategy.calculate(any())).thenReturn(plan);

    Web3ContractPort.PrevalidateResult result = adapter.prevalidate(command(BigInteger.TWO));

    assertThat(result.ok()).isTrue();
    assertThat(result.detail()).containsEntry("gasPriceRpc", "main");
  }

  @Test
  void prevalidate_resolveFeePlan_ignoresNonPositiveGasPrice() throws Exception {
    when(mainWeb3j.ethGetBalance(FROM, DefaultBlockParameterName.PENDING).send())
        .thenReturn(ethBalance(eth("10")));
    when(mainWeb3j.ethCall(any(Transaction.class), eq(DefaultBlockParameterName.PENDING)).send())
        .thenReturn(ethCallUint256(BigInteger.valueOf(100)), ethCallBool(true));
    when(mainWeb3j.ethEstimateGas(any(Transaction.class)).send())
        .thenReturn(ethEstimateGas(BigInteger.valueOf(120_000)));

    EthBaseFee baseFeeError = new EthBaseFee();
    baseFeeError.setError(new Response.Error(1, "base-error"));
    when(mainWeb3j.ethBaseFee().send()).thenReturn(baseFeeError);
    when(subWeb3j.ethBaseFee().send()).thenReturn(baseFeeError);

    EthGasPrice gasPriceZero = new EthGasPrice();
    gasPriceZero.setResult("0x0");
    when(mainWeb3j.ethGasPrice().send()).thenReturn(gasPriceZero);

    when(gasFeeStrategy.calculate(any()))
        .thenReturn(
            new GasFeeStrategy.FeePlan(
                BigInteger.valueOf(200_000),
                BigInteger.valueOf(1_000_000_000L),
                BigInteger.valueOf(2_000_000_000L)));

    Web3ContractPort.PrevalidateResult result = adapter.prevalidate(command(BigInteger.TWO));

    assertThat(result.ok()).isTrue();
    assertThat(result.detail()).containsEntry("maxFeeFallback", "strategy_default");
  }

  @Test
  void broadcast_returnsFailure_whenCommandIsNull() {
    Web3ContractPort.BroadcastResult result = adapter.broadcast(null);

    assertThat(result.success()).isFalse();
    assertThat(result.failureReason()).isEqualTo(Web3TxFailureReason.BROADCAST_FAILED.code());
    assertThat(result.rpcAlias()).isEqualTo("main");
  }

  @Test
  void broadcast_returnsFailure_whenRawTxBlank() {
    Web3ContractPort.BroadcastCommand command = mock(Web3ContractPort.BroadcastCommand.class);
    when(command.rawTx()).thenReturn(" ");

    Web3ContractPort.BroadcastResult result = adapter.broadcast(command);

    assertThat(result.success()).isFalse();
    assertThat(result.failureReason()).isEqualTo(Web3TxFailureReason.BROADCAST_FAILED.code());
    assertThat(result.rpcAlias()).isEqualTo("main");
  }

  @Test
  void broadcast_returnsFailure_whenRawTxNull() {
    Web3ContractPort.BroadcastCommand command = mock(Web3ContractPort.BroadcastCommand.class);
    when(command.rawTx()).thenReturn(null);

    Web3ContractPort.BroadcastResult result = adapter.broadcast(command);

    assertThat(result.success()).isFalse();
    assertThat(result.failureReason()).isEqualTo(Web3TxFailureReason.BROADCAST_FAILED.code());
    assertThat(result.rpcAlias()).isEqualTo("main");
  }

  @Test
  void broadcast_returnsMainSuccess_whenMainBroadcastSucceeds() throws Exception {
    when(mainWeb3j.ethSendRawTransaction("0xabc").send()).thenReturn(sendTx(TX_HASH));

    Web3ContractPort.BroadcastResult result =
        adapter.broadcast(new Web3ContractPort.BroadcastCommand("0xabc"));

    assertThat(result.success()).isTrue();
    assertThat(result.txHash()).isEqualTo(TX_HASH);
    assertThat(result.rpcAlias()).isEqualTo("main");
    verify(subWeb3j, never()).ethSendRawTransaction(any());
  }

  @Test
  void broadcast_usesSub_whenMainHashIsBlank() throws Exception {
    when(mainWeb3j.ethSendRawTransaction("0xabc").send()).thenReturn(sendTx(" "));
    when(subWeb3j.ethSendRawTransaction("0xabc").send()).thenReturn(sendTx(TX_HASH));

    Web3ContractPort.BroadcastResult result =
        adapter.broadcast(new Web3ContractPort.BroadcastCommand("0xabc"));

    assertThat(result.success()).isTrue();
    assertThat(result.txHash()).isEqualTo(TX_HASH);
    assertThat(result.rpcAlias()).isEqualTo("sub");
  }

  @Test
  void broadcast_usesSub_whenMainHashIsNull() throws Exception {
    when(mainWeb3j.ethSendRawTransaction("0xabc").send()).thenReturn(sendTx(null));
    when(subWeb3j.ethSendRawTransaction("0xabc").send()).thenReturn(sendTx(TX_HASH));

    Web3ContractPort.BroadcastResult result =
        adapter.broadcast(new Web3ContractPort.BroadcastCommand("0xabc"));

    assertThat(result.success()).isTrue();
    assertThat(result.txHash()).isEqualTo(TX_HASH);
    assertThat(result.rpcAlias()).isEqualTo("sub");
  }

  @Test
  void broadcast_returnsTreasuryEthLow_whenAnyRpcReturnsInsufficientFundsError() throws Exception {
    when(mainWeb3j.ethSendRawTransaction("0xabc").send()).thenReturn(sendTxError("nonce too low"));
    when(subWeb3j.ethSendRawTransaction("0xabc").send())
        .thenReturn(sendTxError("insufficient funds for gas * price + value"));

    Web3ContractPort.BroadcastResult result =
        adapter.broadcast(new Web3ContractPort.BroadcastCommand("0xabc"));

    assertThat(result.success()).isFalse();
    assertThat(result.failureReason())
        .isEqualTo(Web3TxFailureReason.TREASURY_ETH_BELOW_CRITICAL.code());
    assertThat(result.rpcAlias()).isEqualTo("sub");
  }

  @Test
  void broadcast_prioritizesBroadcastFailed_overRpcUnavailable() throws Exception {
    when(mainWeb3j.ethSendRawTransaction("0xabc").send())
        .thenReturn(sendTxError("replacement transaction underpriced"));
    when(subWeb3j.ethSendRawTransaction("0xabc").send()).thenThrow(new IOException("sub down"));

    Web3ContractPort.BroadcastResult result =
        adapter.broadcast(new Web3ContractPort.BroadcastCommand("0xabc"));

    assertThat(result.success()).isFalse();
    assertThat(result.failureReason()).isEqualTo(Web3TxFailureReason.BROADCAST_FAILED.code());
  }

  @Test
  void broadcast_returnsRpcUnavailable_whenBothRpcsThrow() throws Exception {
    when(mainWeb3j.ethSendRawTransaction("0xabc").send()).thenThrow(new IOException("main down"));
    when(subWeb3j.ethSendRawTransaction("0xabc").send()).thenThrow(new IOException("sub down"));

    Web3ContractPort.BroadcastResult result =
        adapter.broadcast(new Web3ContractPort.BroadcastCommand("0xabc"));

    assertThat(result.success()).isFalse();
    assertThat(result.failureReason()).isEqualTo(Web3TxFailureReason.RPC_UNAVAILABLE.code());
  }

  @Test
  void getReceipt_throws_whenTxHashBlank() {
    assertThatThrownBy(() -> adapter.getReceipt(" "))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("txHash is required");
  }

  @Test
  void getReceipt_throws_whenTxHashNull() {
    assertThatThrownBy(() -> adapter.getReceipt(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("txHash is required");
  }

  @Test
  void getReceipt_returnsMainReceipt_whenMainHasReceipt() throws Exception {
    TransactionReceipt receipt = new TransactionReceipt();
    receipt.setStatus("0x1");
    EthGetTransactionReceipt mainResponse = mock(EthGetTransactionReceipt.class);
    when(mainResponse.hasError()).thenReturn(false);
    when(mainResponse.getTransactionReceipt()).thenReturn(Optional.of(receipt));
    when(mainWeb3j.ethGetTransactionReceipt(TX_HASH).send()).thenReturn(mainResponse);

    Web3ContractPort.ReceiptResult result = adapter.getReceipt(TX_HASH);

    assertThat(result.found()).isTrue();
    assertThat(result.success()).isTrue();
    assertThat(result.rpcAlias()).isEqualTo("main");
    assertThat(result.rpcError()).isFalse();
  }

  @Test
  void getReceipt_returnsNotFound_whenMainResponseHasNoReceipt() throws Exception {
    EthGetTransactionReceipt mainResponse = mock(EthGetTransactionReceipt.class);
    when(mainResponse.hasError()).thenReturn(false);
    when(mainResponse.getTransactionReceipt()).thenReturn(Optional.empty());
    when(mainWeb3j.ethGetTransactionReceipt(TX_HASH).send()).thenReturn(mainResponse);

    Web3ContractPort.ReceiptResult result = adapter.getReceipt(TX_HASH);

    assertThat(result.found()).isFalse();
    assertThat(result.success()).isNull();
    assertThat(result.rpcAlias()).isEqualTo("main");
    assertThat(result.rpcError()).isFalse();
  }

  @Test
  void getReceipt_usesSub_whenMainFailsAndSubReturnsReceipt() throws Exception {
    EthGetTransactionReceipt mainError = new EthGetTransactionReceipt();
    mainError.setError(new Response.Error(1, "main failed"));
    when(mainWeb3j.ethGetTransactionReceipt(TX_HASH).send()).thenReturn(mainError);

    TransactionReceipt receipt = new TransactionReceipt();
    receipt.setStatus("0x0");
    EthGetTransactionReceipt subResponse = mock(EthGetTransactionReceipt.class);
    when(subResponse.hasError()).thenReturn(false);
    when(subResponse.getTransactionReceipt()).thenReturn(Optional.of(receipt));
    when(subWeb3j.ethGetTransactionReceipt(TX_HASH).send()).thenReturn(subResponse);

    Web3ContractPort.ReceiptResult result = adapter.getReceipt(TX_HASH);

    assertThat(result.found()).isTrue();
    assertThat(result.success()).isFalse();
    assertThat(result.rpcAlias()).isEqualTo("sub");
    assertThat(result.rpcError()).isFalse();
  }

  @Test
  void getReceipt_returnsRpcError_whenBothRpcsFail() throws Exception {
    when(mainWeb3j.ethGetTransactionReceipt(TX_HASH).send()).thenThrow(new IOException("main down"));
    when(subWeb3j.ethGetTransactionReceipt(TX_HASH).send()).thenThrow(new IOException("sub down"));

    Web3ContractPort.ReceiptResult result = adapter.getReceipt(TX_HASH);

    assertThat(result.found()).isFalse();
    assertThat(result.rpcError()).isTrue();
    assertThat(result.rpcAlias()).isEqualTo("sub");
    assertThat(result.failureReason()).isEqualTo(Web3TxFailureReason.RPC_UNAVAILABLE.code());
  }

  @Test
  void shutdown_closesBothClients_whenInitialized() {
    adapter.shutdown();

    verify(mainWeb3j).shutdown();
    verify(subWeb3j).shutdown();
  }

  @Test
  void shutdown_doesNothing_whenClientsAreNull() {
    ReflectionTestUtils.setField(adapter, "mainWeb3j", null);
    ReflectionTestUtils.setField(adapter, "subWeb3j", null);

    adapter.shutdown();
  }

  @Test
  void classifyBroadcastFailureMessage_handlesKnownAndUnknownMessages() {
    assertThat(
            Web3jErc20Adapter.classifyBroadcastFailureMessage(
                "insufficient funds for gas * price + value"))
        .isEqualTo(Web3TxFailureReason.TREASURY_ETH_BELOW_CRITICAL);
    assertThat(Web3jErc20Adapter.classifyBroadcastFailureMessage("insufficient balance"))
        .isEqualTo(Web3TxFailureReason.TREASURY_ETH_BELOW_CRITICAL);
    assertThat(Web3jErc20Adapter.classifyBroadcastFailureMessage("not enough funds"))
        .isEqualTo(Web3TxFailureReason.TREASURY_ETH_BELOW_CRITICAL);
    assertThat(Web3jErc20Adapter.classifyBroadcastFailureMessage("nonce too low"))
        .isEqualTo(Web3TxFailureReason.BROADCAST_FAILED);
    assertThat(Web3jErc20Adapter.classifyBroadcastFailureMessage(" "))
        .isEqualTo(Web3TxFailureReason.BROADCAST_FAILED);
    assertThat(Web3jErc20Adapter.classifyBroadcastFailureMessage(null))
        .isEqualTo(Web3TxFailureReason.BROADCAST_FAILED);
  }

  private Web3ContractPort.PrevalidateCommand command(BigInteger amountWei) {
    return new Web3ContractPort.PrevalidateCommand(FROM, TO, amountWei);
  }

  private BigInteger eth(String ethAmount) {
    return new BigDecimal(ethAmount).multiply(new BigDecimal("1000000000000000000")).toBigInteger();
  }

  private EthGetBalance ethBalance(BigInteger weiAmount) {
    EthGetBalance response = new EthGetBalance();
    response.setResult(Numeric.encodeQuantity(weiAmount));
    return response;
  }

  private EthCall ethCallUint256(BigInteger value) {
    EthCall response = new EthCall();
    response.setResult(Numeric.toHexStringWithPrefixZeroPadded(value, 64));
    return response;
  }

  private EthCall ethCallBool(boolean value) {
    EthCall response = new EthCall();
    response.setResult(Numeric.toHexStringWithPrefixZeroPadded(value ? BigInteger.ONE : BigInteger.ZERO, 64));
    return response;
  }

  private EthEstimateGas ethEstimateGas(BigInteger gas) {
    EthEstimateGas response = new EthEstimateGas();
    response.setResult(Numeric.encodeQuantity(gas));
    return response;
  }

  private EthSendTransaction sendTx(String txHash) {
    EthSendTransaction response = new EthSendTransaction();
    response.setResult(txHash);
    return response;
  }

  private EthSendTransaction sendTxError(String message) {
    EthSendTransaction response = new EthSendTransaction();
    response.setError(new Response.Error(1, message));
    return response;
  }
}
