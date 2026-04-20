package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.DefaultGasFeeCalculator;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.TransactionRewardTokenProperties;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthBaseFee;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthMaxPriorityFeePerGas;

@DisplayName("QnaContractCallSupport unit test")
class QnaContractCallSupportTest {

  private static final String FROM = "0x" + "1".repeat(40);
  private static final String TO = "0x" + "2".repeat(40);
  private static final String CALL_DATA = "0x1234";

  private QnaContractCallSupport support;
  private Web3j mainWeb3j;
  private Web3j subWeb3j;

  @BeforeEach
  void setUp() {
    Web3CoreProperties coreProperties = new Web3CoreProperties();
    Web3CoreProperties.Rpc rpc = new Web3CoreProperties.Rpc();
    rpc.setMain("http://localhost:8545");
    rpc.setSub("http://localhost:8546");
    coreProperties.setRpc(rpc);

    TransactionRewardTokenProperties rewardTokenProperties = new TransactionRewardTokenProperties();
    rewardTokenProperties.setEnabled(true);
    rewardTokenProperties.setTokenContractAddress("0x" + "3".repeat(40));
    TransactionRewardTokenProperties.Gas gas = new TransactionRewardTokenProperties.Gas();
    gas.setDefaultGasLimit(210_000L);
    gas.setDefaultMaxPriorityFeePerGasWei(1_000_000_000L);
    gas.setMaxFeeMultiplier(2);
    rewardTokenProperties.setGas(gas);

    support =
        new QnaContractCallSupport(
            coreProperties, new DefaultGasFeeCalculator(rewardTokenProperties));

    mainWeb3j = mock(Web3j.class, RETURNS_DEEP_STUBS);
    subWeb3j = mock(Web3j.class, RETURNS_DEEP_STUBS);
    ReflectionTestUtils.setField(support, "mainWeb3j", mainWeb3j);
    ReflectionTestUtils.setField(support, "subWeb3j", subWeb3j);
  }

  @Test
  @DisplayName("prevalidateContractCall falls back to gasPrice when eth_baseFee is unavailable")
  void prevalidateContractCall_fallsBackToGasPrice_whenBaseFeeUnavailable() throws Exception {
    when(mainWeb3j.ethCall(any(Transaction.class), eq(DefaultBlockParameterName.PENDING)).send())
        .thenReturn(ethCallSuccess());
    when(mainWeb3j.ethEstimateGas(any(Transaction.class)).send())
        .thenReturn(ethEstimateGas(BigInteger.valueOf(120_000)));

    EthMaxPriorityFeePerGas priorityResponse = new EthMaxPriorityFeePerGas();
    priorityResponse.setResult("0x77359400"); // 2 gwei
    when(mainWeb3j.ethMaxPriorityFeePerGas().send()).thenReturn(priorityResponse);

    EthBaseFee mainBaseError = new EthBaseFee();
    mainBaseError.setError(new Response.Error(1, "base-main-error"));
    EthBaseFee subBaseError = new EthBaseFee();
    subBaseError.setError(new Response.Error(2, "base-sub-error"));
    when(mainWeb3j.ethBaseFee().send()).thenReturn(mainBaseError);
    when(subWeb3j.ethBaseFee().send()).thenReturn(subBaseError);

    EthGasPrice mainGasError = new EthGasPrice();
    mainGasError.setError(new Response.Error(3, "gas-main-error"));
    EthGasPrice subGasResponse = new EthGasPrice();
    subGasResponse.setResult("0xb2d05e00"); // 3 gwei
    when(mainWeb3j.ethGasPrice().send()).thenReturn(mainGasError);
    when(subWeb3j.ethGasPrice().send()).thenReturn(subGasResponse);

    QnaContractCallSupport.QnaCallPrevalidationResult result =
        support.prevalidateContractCall(FROM, TO, CALL_DATA);

    assertThat(result.gasLimit()).isEqualTo(BigInteger.valueOf(120_000));
    assertThat(result.maxPriorityFeePerGas()).isEqualTo(gwei(2));
    assertThat(result.maxFeePerGas()).isEqualTo(gwei(5));
  }

  @Test
  @DisplayName("prevalidateContractCall uses strategy defaults when fee RPCs are unavailable")
  void prevalidateContractCall_usesStrategyDefaults_whenFeeRpcsUnavailable() throws Exception {
    when(mainWeb3j.ethCall(any(Transaction.class), eq(DefaultBlockParameterName.PENDING)).send())
        .thenReturn(ethCallSuccess());
    when(mainWeb3j.ethEstimateGas(any(Transaction.class)).send())
        .thenReturn(ethEstimateGas(BigInteger.valueOf(130_000)));

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

    QnaContractCallSupport.QnaCallPrevalidationResult result =
        support.prevalidateContractCall(FROM, TO, CALL_DATA);

    assertThat(result.gasLimit()).isEqualTo(BigInteger.valueOf(130_000));
    assertThat(result.maxPriorityFeePerGas()).isEqualTo(gwei(1));
    assertThat(result.maxFeePerGas()).isEqualTo(gwei(2));
  }

  private EthCall ethCallSuccess() {
    EthCall response = new EthCall();
    response.setResult("0x");
    return response;
  }

  private EthEstimateGas ethEstimateGas(BigInteger amount) {
    EthEstimateGas response = new EthEstimateGas();
    response.setResult(org.web3j.utils.Numeric.encodeQuantity(amount));
    return response;
  }

  private BigInteger gwei(long value) {
    return BigInteger.valueOf(value).multiply(BigInteger.valueOf(1_000_000_000L));
  }
}
