package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;

class MarketplaceContractCallSupportTest {

  private static final String OWNER = "0x1111111111111111111111111111111111111111";
  private static final String SPENDER = "0x2222222222222222222222222222222222222222";
  private static final String TOKEN = "0x3333333333333333333333333333333333333333";
  private static final String ESCROW = "0x4444444444444444444444444444444444444444";

  private MarketplaceContractCallSupport support;
  private Web3j mainWeb3j;
  private Web3j subWeb3j;

  @BeforeEach
  void setUp() {
    Web3CoreProperties coreProperties = new Web3CoreProperties();
    coreProperties.getRpc().setMain("http://localhost:8545");
    coreProperties.getRpc().setSub("http://localhost:8546");
    support = new MarketplaceContractCallSupport(coreProperties);
    mainWeb3j = mock(Web3j.class);
    subWeb3j = mock(Web3j.class);
    ReflectionTestUtils.setField(support, "mainWeb3j", mainWeb3j);
    ReflectionTestUtils.setField(support, "subWeb3j", subWeb3j);
  }

  @Test
  void loadAllowance_usesSubRpc_whenMainRpcThrows() throws Exception {
    stubEthCall(mainWeb3j, new IOException("main down"));
    stubEthCall(subWeb3j, ethCallResult(new Uint256(BigInteger.valueOf(123L))));

    BigInteger allowance = support.loadAllowance(OWNER, SPENDER, TOKEN);

    assertThat(allowance).isEqualTo(BigInteger.valueOf(123L));
    verify(subWeb3j).ethCall(any(Transaction.class), eq(DefaultBlockParameterName.PENDING));
  }

  @Test
  void isSupportedToken_throws_whenBothMainAndSubFail() throws Exception {
    EthCall mainError = new EthCall();
    mainError.setError(new Response.Error(1, "main error"));
    EthCall subError = new EthCall();
    subError.setError(new Response.Error(2, "sub error"));
    stubEthCall(mainWeb3j, mainError);
    stubEthCall(subWeb3j, subError);

    assertThatThrownBy(() -> support.isSupportedToken(ESCROW, TOKEN))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("main error")
        .hasMessageContaining("sub error");
  }

  @Test
  void loadBalance_throws_whenEthCallReverts() throws Exception {
    stubEthCall(mainWeb3j, ethCallRevert("balance reverted"));

    assertThatThrownBy(() -> support.loadBalance(OWNER, TOKEN))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("balanceOf eth_call reverted")
        .hasMessageContaining("balance reverted");
  }

  @SuppressWarnings("rawtypes")
  private void stubEthCall(Web3j web3j, EthCall response) throws Exception {
    Request request = mock(Request.class);
    when(request.send()).thenReturn(response);
    doReturn(request)
        .when(web3j)
        .ethCall(any(Transaction.class), eq(DefaultBlockParameterName.PENDING));
  }

  @SuppressWarnings("rawtypes")
  private void stubEthCall(Web3j web3j, IOException exception) throws Exception {
    Request request = mock(Request.class);
    when(request.send()).thenThrow(exception);
    doReturn(request)
        .when(web3j)
        .ethCall(any(Transaction.class), eq(DefaultBlockParameterName.PENDING));
  }

  private EthCall ethCallResult(Type<?> value) {
    EthCall response = new EthCall();
    response.setResult(FunctionEncoder.encodeConstructor(List.of(value)));
    return response;
  }

  private EthCall ethCallRevert(String reason) {
    EthCall response = new EthCall();
    response.setResult("0x08c379a0" + abiEncodedString(reason));
    return response;
  }

  private String abiEncodedString(String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    String hex = org.web3j.utils.Numeric.toHexStringNoPrefix(bytes);
    int paddedLength = ((hex.length() + 63) / 64) * 64;
    return leftPadHex(BigInteger.valueOf(32))
        + leftPadHex(BigInteger.valueOf(bytes.length))
        + rightPadHex(hex, paddedLength);
  }

  private String leftPadHex(BigInteger value) {
    return String.format("%064x", value);
  }

  private String rightPadHex(String value, int targetLength) {
    return value + "0".repeat(targetLength - value.length());
  }
}
