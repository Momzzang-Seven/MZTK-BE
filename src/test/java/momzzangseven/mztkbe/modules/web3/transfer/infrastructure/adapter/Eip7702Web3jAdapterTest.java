package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.Eip7702ChainPort;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.config.Eip7702Properties;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.config.TransferCoreProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.web3j.protocol.Web3j;
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

class Eip7702Web3jAdapterTest {

  private static final String ADDRESS = "0x" + "a".repeat(40);

  private Eip7702Web3jAdapter adapter;
  private Eip7702Properties eip7702Properties;

  @BeforeEach
  void setUp() {
    TransferCoreProperties coreProperties = new TransferCoreProperties();
    coreProperties.getRpc().setMain("http://localhost:8545");
    coreProperties.getRpc().setSub("http://localhost:8546");

    eip7702Properties = new Eip7702Properties();
    eip7702Properties.getSponsor().setMaxPriorityFeeGwei(2L);
    eip7702Properties.getSponsor().setMaxMaxFeeGwei(60L);

    adapter = new Eip7702Web3jAdapter(coreProperties, eip7702Properties);
  }

  @Test
  void loadPendingAccountNonce_returnsMainNonce_whenMainRpcSucceeds() throws Exception {
    Web3j mainWeb3j = mock(Web3j.class, RETURNS_DEEP_STUBS);
    Web3j subWeb3j = mock(Web3j.class, RETURNS_DEEP_STUBS);
    EthGetTransactionCount mainResponse = new EthGetTransactionCount();
    mainResponse.setResult("0x2a");

    when(mainWeb3j.ethGetTransactionCount(ADDRESS, DefaultBlockParameterName.PENDING).send())
        .thenReturn(mainResponse);

    injectWeb3Clients(mainWeb3j, subWeb3j);

    BigInteger nonce = adapter.loadPendingAccountNonce(ADDRESS);

    assertThat(nonce).isEqualTo(BigInteger.valueOf(42));
    verify(subWeb3j, never()).ethGetTransactionCount(any(), any());
  }

  @Test
  void loadPendingAccountNonce_usesSubRpc_whenMainReturnsError() throws Exception {
    Web3j mainWeb3j = mock(Web3j.class, RETURNS_DEEP_STUBS);
    Web3j subWeb3j = mock(Web3j.class, RETURNS_DEEP_STUBS);

    EthGetTransactionCount mainResponse = new EthGetTransactionCount();
    mainResponse.setError(new Response.Error(1, "main error"));
    EthGetTransactionCount subResponse = new EthGetTransactionCount();
    subResponse.setResult("0x03");

    when(mainWeb3j.ethGetTransactionCount(ADDRESS, DefaultBlockParameterName.PENDING).send())
        .thenReturn(mainResponse);
    when(subWeb3j.ethGetTransactionCount(ADDRESS, DefaultBlockParameterName.PENDING).send())
        .thenReturn(subResponse);

    injectWeb3Clients(mainWeb3j, subWeb3j);

    BigInteger nonce = adapter.loadPendingAccountNonce(ADDRESS);

    assertThat(nonce).isEqualTo(BigInteger.valueOf(3));
  }

  @Test
  void loadPendingAccountNonce_throws_whenBothMainAndSubFail() throws Exception {
    Web3j mainWeb3j = mock(Web3j.class, RETURNS_DEEP_STUBS);
    Web3j subWeb3j = mock(Web3j.class, RETURNS_DEEP_STUBS);

    EthGetTransactionCount subResponse = new EthGetTransactionCount();
    subResponse.setError(new Response.Error(1, "sub error"));

    when(mainWeb3j.ethGetTransactionCount(ADDRESS, DefaultBlockParameterName.PENDING).send())
        .thenThrow(new IOException("network down"));
    when(subWeb3j.ethGetTransactionCount(ADDRESS, DefaultBlockParameterName.PENDING).send())
        .thenReturn(subResponse);

    injectWeb3Clients(mainWeb3j, subWeb3j);

    assertThatThrownBy(() -> adapter.loadPendingAccountNonce(ADDRESS))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("sub error");
  }

  @Test
  void loadSponsorFeePlan_appliesPriorityCap_andUsesBaseFeeWhenAvailable() throws Exception {
    Web3j mainWeb3j = mock(Web3j.class, RETURNS_DEEP_STUBS);
    Web3j subWeb3j = mock(Web3j.class, RETURNS_DEEP_STUBS);

    EthMaxPriorityFeePerGas priorityResponse = new EthMaxPriorityFeePerGas();
    priorityResponse.setResult("0xba43b7400"); // 50 gwei
    EthBaseFee baseFeeResponse = new EthBaseFee();
    baseFeeResponse.setResult("0x3b9aca00"); // 1 gwei

    when(mainWeb3j.ethMaxPriorityFeePerGas().send()).thenReturn(priorityResponse);
    when(mainWeb3j.ethBaseFee().send()).thenReturn(baseFeeResponse);

    injectWeb3Clients(mainWeb3j, subWeb3j);

    Eip7702ChainPort.FeePlan feePlan = adapter.loadSponsorFeePlan();

    assertThat(feePlan.maxPriorityFeePerGas()).isEqualTo(gwei(2));
    assertThat(feePlan.maxFeePerGas()).isEqualTo(gwei(4));
  }

  @Test
  void loadSponsorFeePlan_fallsBackToGasPrice_whenBaseFeeUnavailable() throws Exception {
    Web3j mainWeb3j = mock(Web3j.class, RETURNS_DEEP_STUBS);
    Web3j subWeb3j = mock(Web3j.class, RETURNS_DEEP_STUBS);

    EthMaxPriorityFeePerGas mainPriorityError = new EthMaxPriorityFeePerGas();
    mainPriorityError.setError(new Response.Error(1, "priority main error"));
    EthMaxPriorityFeePerGas subPriorityError = new EthMaxPriorityFeePerGas();
    subPriorityError.setError(new Response.Error(2, "priority sub error"));
    EthBaseFee mainBaseError = new EthBaseFee();
    mainBaseError.setError(new Response.Error(1, "base main error"));
    EthBaseFee subBaseError = new EthBaseFee();
    subBaseError.setError(new Response.Error(2, "base sub error"));
    EthGasPrice mainGasError = new EthGasPrice();
    mainGasError.setError(new Response.Error(1, "gas main error"));
    EthGasPrice subGas = new EthGasPrice();
    subGas.setResult("0xb2d05e00"); // 3 gwei

    when(mainWeb3j.ethMaxPriorityFeePerGas().send()).thenReturn(mainPriorityError);
    when(subWeb3j.ethMaxPriorityFeePerGas().send()).thenReturn(subPriorityError);
    when(mainWeb3j.ethBaseFee().send()).thenReturn(mainBaseError);
    when(subWeb3j.ethBaseFee().send()).thenReturn(subBaseError);
    when(mainWeb3j.ethGasPrice().send()).thenReturn(mainGasError);
    when(subWeb3j.ethGasPrice().send()).thenReturn(subGas);

    injectWeb3Clients(mainWeb3j, subWeb3j);

    Eip7702ChainPort.FeePlan feePlan = adapter.loadSponsorFeePlan();

    assertThat(feePlan.maxPriorityFeePerGas()).isEqualTo(gwei(2));
    assertThat(feePlan.maxFeePerGas()).isEqualTo(gwei(8));
  }

  @Test
  void loadSponsorFeePlan_enforcesMaxFeeLowerBoundToPriority_whenCapTooLow() throws Exception {
    eip7702Properties.getSponsor().setMaxPriorityFeeGwei(2L);
    eip7702Properties.getSponsor().setMaxMaxFeeGwei(1L);

    Web3j mainWeb3j = mock(Web3j.class, RETURNS_DEEP_STUBS);
    Web3j subWeb3j = mock(Web3j.class, RETURNS_DEEP_STUBS);

    EthMaxPriorityFeePerGas priorityError = new EthMaxPriorityFeePerGas();
    priorityError.setError(new Response.Error(1, "priority error"));
    EthBaseFee baseError = new EthBaseFee();
    baseError.setError(new Response.Error(1, "base error"));
    EthGasPrice gasError = new EthGasPrice();
    gasError.setError(new Response.Error(1, "gas error"));

    when(mainWeb3j.ethMaxPriorityFeePerGas().send()).thenReturn(priorityError);
    when(subWeb3j.ethMaxPriorityFeePerGas().send()).thenReturn(priorityError);
    when(mainWeb3j.ethBaseFee().send()).thenReturn(baseError);
    when(subWeb3j.ethBaseFee().send()).thenReturn(baseError);
    when(mainWeb3j.ethGasPrice().send()).thenReturn(gasError);
    when(subWeb3j.ethGasPrice().send()).thenReturn(gasError);

    injectWeb3Clients(mainWeb3j, subWeb3j);

    Eip7702ChainPort.FeePlan feePlan = adapter.loadSponsorFeePlan();

    assertThat(feePlan.maxPriorityFeePerGas()).isEqualTo(gwei(2));
    assertThat(feePlan.maxFeePerGas()).isEqualTo(gwei(2));
  }

  @Test
  void loadSponsorFeePlan_ignoresNonPositiveRpcPriority_andKeepsCap() throws Exception {
    Web3j mainWeb3j = mock(Web3j.class, RETURNS_DEEP_STUBS);
    Web3j subWeb3j = mock(Web3j.class, RETURNS_DEEP_STUBS);

    EthMaxPriorityFeePerGas priorityResponse = new EthMaxPriorityFeePerGas();
    priorityResponse.setResult("0x0");
    EthBaseFee baseFeeResponse = new EthBaseFee();
    baseFeeResponse.setResult("0x3b9aca00"); // 1 gwei

    when(mainWeb3j.ethMaxPriorityFeePerGas().send()).thenReturn(priorityResponse);
    when(mainWeb3j.ethBaseFee().send()).thenReturn(baseFeeResponse);

    injectWeb3Clients(mainWeb3j, subWeb3j);

    Eip7702ChainPort.FeePlan feePlan = adapter.loadSponsorFeePlan();

    assertThat(feePlan.maxPriorityFeePerGas()).isEqualTo(gwei(2));
  }

  @Test
  void estimateGasWithAuthorization_returnsMainAmount_whenMainSucceeds() throws Exception {
    HttpService mainService = mock(HttpService.class);
    HttpService subService = mock(HttpService.class);
    EthEstimateGas mainResult = new EthEstimateGas();
    mainResult.setResult("0x5208");

    when(mainService.send(any(Request.class), eq(EthEstimateGas.class))).thenReturn(mainResult);
    injectHttpServices(mainService, subService);

    BigInteger amount =
        adapter.estimateGasWithAuthorization(
            ADDRESS, "0x" + "b".repeat(40), "0xdeadbeef", List.of(validAuthTuple()));

    assertThat(amount).isEqualTo(BigInteger.valueOf(21000));
    verify(subService, never()).send(any(Request.class), eq(EthEstimateGas.class));
  }

  @Test
  void estimateGasWithAuthorization_usesSub_whenMainHasError() throws Exception {
    HttpService mainService = mock(HttpService.class);
    HttpService subService = mock(HttpService.class);
    EthEstimateGas mainError = new EthEstimateGas();
    mainError.setError(new Response.Error(1, "main estimate error"));
    EthEstimateGas subResult = new EthEstimateGas();
    subResult.setResult("0x5300");

    when(mainService.send(any(Request.class), eq(EthEstimateGas.class))).thenReturn(mainError);
    when(subService.send(any(Request.class), eq(EthEstimateGas.class))).thenReturn(subResult);
    injectHttpServices(mainService, subService);

    BigInteger amount =
        adapter.estimateGasWithAuthorization(
            ADDRESS, "0x" + "b".repeat(40), "0xdeadbeef", List.of(validAuthTuple()));

    assertThat(amount).isEqualTo(BigInteger.valueOf(21248));
  }

  @Test
  void estimateGasWithAuthorization_throws_whenBothMainAndSubHaveErrors() throws Exception {
    HttpService mainService = mock(HttpService.class);
    HttpService subService = mock(HttpService.class);
    EthEstimateGas mainError = new EthEstimateGas();
    mainError.setError(new Response.Error(1, "main estimate error"));
    EthEstimateGas subError = new EthEstimateGas();
    subError.setError(new Response.Error(2, "sub estimate error"));

    when(mainService.send(any(Request.class), eq(EthEstimateGas.class))).thenReturn(mainError);
    when(subService.send(any(Request.class), eq(EthEstimateGas.class))).thenReturn(subError);
    injectHttpServices(mainService, subService);

    assertThatThrownBy(
            () ->
                adapter.estimateGasWithAuthorization(
                    ADDRESS, "0x" + "b".repeat(40), "0xdeadbeef", List.of(validAuthTuple())))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("eth_estimateGas failed")
        .hasMessageContaining("sub estimate error");
  }

  @Test
  void estimateGasWithAuthorization_fallsBackToMainErrorMessage_whenSubErrorMessageMissing()
      throws Exception {
    HttpService mainService = mock(HttpService.class);
    HttpService subService = mock(HttpService.class);
    EthEstimateGas mainError = mock(EthEstimateGas.class);
    EthEstimateGas subErrorWithoutMessage = mock(EthEstimateGas.class);
    Response.Error error = new Response.Error(1, "main estimate error");

    when(mainError.hasError()).thenReturn(true);
    when(mainError.getError()).thenReturn(error);
    when(subErrorWithoutMessage.hasError()).thenReturn(true);
    when(subErrorWithoutMessage.getError()).thenReturn(null);

    when(mainService.send(any(Request.class), eq(EthEstimateGas.class))).thenReturn(mainError);
    when(subService.send(any(Request.class), eq(EthEstimateGas.class)))
        .thenReturn(subErrorWithoutMessage);
    injectHttpServices(mainService, subService);

    assertThatThrownBy(
            () ->
                adapter.estimateGasWithAuthorization(
                    ADDRESS, "0x" + "b".repeat(40), "0xdeadbeef", List.of(validAuthTuple())))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("main estimate error");
  }

  @Test
  void estimateGasWithAuthorization_throwsWithExceptionClass_whenRpcThrowsIOException()
      throws Exception {
    HttpService mainService = mock(HttpService.class);
    HttpService subService = mock(HttpService.class);
    when(mainService.send(any(Request.class), eq(EthEstimateGas.class)))
        .thenThrow(new IOException("io fail"));
    injectHttpServices(mainService, subService);

    assertThatThrownBy(
            () ->
                adapter.estimateGasWithAuthorization(
                    ADDRESS, "0x" + "b".repeat(40), "0xdeadbeef", List.of(validAuthTuple())))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("IOException");
  }

  @Test
  void estimateGasWithAuthorization_throwsUnknown_whenBothErrorsHaveNoMessage() throws Exception {
    HttpService mainService = mock(HttpService.class);
    HttpService subService = mock(HttpService.class);
    EthEstimateGas mainError = mock(EthEstimateGas.class);
    EthEstimateGas subError = mock(EthEstimateGas.class);

    when(mainError.hasError()).thenReturn(true);
    when(mainError.getError()).thenReturn(null);
    when(subError.hasError()).thenReturn(true);
    when(subError.getError()).thenReturn(null);
    when(mainService.send(any(Request.class), eq(EthEstimateGas.class))).thenReturn(mainError);
    when(subService.send(any(Request.class), eq(EthEstimateGas.class))).thenReturn(subError);
    injectHttpServices(mainService, subService);

    assertThatThrownBy(
            () ->
                adapter.estimateGasWithAuthorization(
                    ADDRESS, "0x" + "b".repeat(40), "0xdeadbeef", List.of(validAuthTuple())))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("eth_estimateGas failed: UNKNOWN");
  }

  @Test
  void loadPendingAccountNonce_throwsUnknown_whenBothRpcResponsesAreNull() throws Exception {
    Web3j mainWeb3j = mock(Web3j.class, RETURNS_DEEP_STUBS);
    Web3j subWeb3j = mock(Web3j.class, RETURNS_DEEP_STUBS);

    when(mainWeb3j.ethGetTransactionCount(ADDRESS, DefaultBlockParameterName.PENDING).send())
        .thenReturn(null);
    when(subWeb3j.ethGetTransactionCount(ADDRESS, DefaultBlockParameterName.PENDING).send())
        .thenReturn(null);
    injectWeb3Clients(mainWeb3j, subWeb3j);

    assertThatThrownBy(() -> adapter.loadPendingAccountNonce(ADDRESS))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("UNKNOWN");
  }

  @Test
  void loadPendingAccountNonce_usesSubExceptionMessage_whenMainAndSubReturnNoResponse()
      throws Exception {
    Web3j mainWeb3j = mock(Web3j.class, RETURNS_DEEP_STUBS);
    Web3j subWeb3j = mock(Web3j.class, RETURNS_DEEP_STUBS);

    when(mainWeb3j.ethGetTransactionCount(ADDRESS, DefaultBlockParameterName.PENDING).send())
        .thenReturn(null);
    when(subWeb3j.ethGetTransactionCount(ADDRESS, DefaultBlockParameterName.PENDING).send())
        .thenThrow(new IOException("sub down"));
    injectWeb3Clients(mainWeb3j, subWeb3j);

    assertThatThrownBy(() -> adapter.loadPendingAccountNonce(ADDRESS))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("IOException");
  }

  @Test
  void shutdown_closesBothClients_whenInitialized() {
    Web3j mainWeb3j = mock(Web3j.class);
    Web3j subWeb3j = mock(Web3j.class);
    injectWeb3Clients(mainWeb3j, subWeb3j);

    adapter.shutdown();

    verify(mainWeb3j).shutdown();
    verify(subWeb3j).shutdown();
  }

  @Test
  void shutdown_doesNothing_whenClientsNotInitialized() {
    adapter.shutdown();
  }

  private void injectWeb3Clients(Web3j mainWeb3j, Web3j subWeb3j) {
    ReflectionTestUtils.setField(adapter, "mainWeb3j", mainWeb3j);
    ReflectionTestUtils.setField(adapter, "subWeb3j", subWeb3j);
  }

  private void injectHttpServices(HttpService mainService, HttpService subService) {
    ReflectionTestUtils.setField(adapter, "mainService", mainService);
    ReflectionTestUtils.setField(adapter, "subService", subService);
  }

  private Eip7702ChainPort.AuthorizationTuple validAuthTuple() {
    return new Eip7702ChainPort.AuthorizationTuple(
        BigInteger.valueOf(11155111L),
        "0x" + "c".repeat(40),
        BigInteger.ONE,
        BigInteger.ZERO,
        BigInteger.ONE,
        BigInteger.TWO);
  }

  private BigInteger gwei(long value) {
    return Convert.toWei(Long.toString(value), Convert.Unit.GWEI).toBigIntegerExact();
  }
}
