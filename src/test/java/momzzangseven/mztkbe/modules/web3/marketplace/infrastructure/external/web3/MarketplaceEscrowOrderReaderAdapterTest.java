package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.MarketplaceEscrowOrderView;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceEscrowIdCodec;
import momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.config.MarketplaceEscrowProperties;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint16;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint48;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;

class MarketplaceEscrowOrderReaderAdapterTest {

  private static final String ESCROW = "0x1111111111111111111111111111111111111111";
  private static final String TOKEN = "0x2222222222222222222222222222222222222222";
  private static final String BUYER = "0x3333333333333333333333333333333333333333";
  private static final String TRAINER = "0x4444444444444444444444444444444444444444";
  private static final String FIRST_ORDER_KEY =
      "0x0000000000000000000000000000000011111111111111111111111111111111";
  private static final String SECOND_ORDER_KEY =
      "0x0000000000000000000000000000000022222222222222222222222222222222";

  private MarketplaceEscrowOrderReaderAdapter adapter;
  private Web3j mainWeb3j;
  private Web3j subWeb3j;

  @BeforeEach
  void setUp() {
    Web3CoreProperties coreProperties = new Web3CoreProperties();
    coreProperties.getRpc().setMain("http://localhost:8545");
    coreProperties.getRpc().setSub("http://localhost:8546");

    MarketplaceEscrowProperties escrowProperties = new MarketplaceEscrowProperties();
    escrowProperties.setMarketplaceContractAddress(ESCROW);

    adapter = new MarketplaceEscrowOrderReaderAdapter(coreProperties, escrowProperties);
    mainWeb3j = mock(Web3j.class);
    subWeb3j = mock(Web3j.class);
    ReflectionTestUtils.setField(adapter, "mainWeb3j", mainWeb3j);
    ReflectionTestUtils.setField(adapter, "subWeb3j", subWeb3j);
  }

  @Test
  void getOrder_decodesStaticStructReturnData() throws Exception {
    stubMainEthCall(encodeReturn(order(FIRST_ORDER_KEY, 1_000L, 123_456L, 1000)));

    MarketplaceEscrowOrderView result = adapter.getOrder(FIRST_ORDER_KEY);

    assertThat(result.orderKey()).isEqualTo(FIRST_ORDER_KEY);
    assertThat(result.price()).isEqualTo(BigInteger.valueOf(1_000L));
    assertThat(result.tokenAddress()).isEqualToIgnoringCase(TOKEN);
    assertThat(result.deadlineEpochSeconds()).isEqualTo(123_456L);
    assertThat(result.state()).isEqualTo(MarketplaceEscrowOrderView.STATE_CREATED);
    assertThat(result.buyerAddress()).isEqualToIgnoringCase(BUYER);
    assertThat(result.trainerAddress()).isEqualToIgnoringCase(TRAINER);
    verify(subWeb3j, never()).ethCall(any(Transaction.class), any(DefaultBlockParameterName.class));
  }

  @Test
  void getOrders_decodesDynamicArrayReturnData() throws Exception {
    DynamicArray<MarketplaceEscrowOrderReaderAdapter.ClassOrder> orders =
        new DynamicArray<>(
            MarketplaceEscrowOrderReaderAdapter.ClassOrder.class,
            order(FIRST_ORDER_KEY, 1_000L, 123_456L, 1000),
            order(SECOND_ORDER_KEY, 2_000L, 654_321L, 3000));
    stubMainEthCall(encodeReturn(orders));

    List<MarketplaceEscrowOrderView> results =
        adapter.getOrders(List.of(FIRST_ORDER_KEY, SECOND_ORDER_KEY));

    assertThat(results).hasSize(2);
    assertThat(results.get(0).orderKey()).isEqualTo(FIRST_ORDER_KEY);
    assertThat(results.get(0).price()).isEqualTo(BigInteger.valueOf(1_000L));
    assertThat(results.get(0).state()).isEqualTo(MarketplaceEscrowOrderView.STATE_CREATED);
    assertThat(results.get(1).orderKey()).isEqualTo(SECOND_ORDER_KEY);
    assertThat(results.get(1).price()).isEqualTo(BigInteger.valueOf(2_000L));
    assertThat(results.get(1).state()).isEqualTo(MarketplaceEscrowOrderView.STATE_CANCELLED);
  }

  private void stubMainEthCall(String encodedReturnData) throws Exception {
    EthCall response = new EthCall();
    response.setResult(encodedReturnData);
    @SuppressWarnings("rawtypes")
    Request request = mock(Request.class);
    when(request.send()).thenReturn(response);
    doReturn(request)
        .when(mainWeb3j)
        .ethCall(any(Transaction.class), eq(DefaultBlockParameterName.PENDING));
  }

  private String encodeReturn(Type<?> value) {
    return FunctionEncoder.encodeConstructor(List.of(value));
  }

  private MarketplaceEscrowOrderReaderAdapter.ClassOrder order(
      String orderKey, long price, long deadline, int state) {
    return new MarketplaceEscrowOrderReaderAdapter.ClassOrder(
        new Bytes32(MarketplaceEscrowIdCodec.orderKeyBytes(orderKey)),
        new Uint256(BigInteger.valueOf(price)),
        new Address(TOKEN),
        new Uint48(BigInteger.valueOf(deadline)),
        new Uint16(BigInteger.valueOf(state)),
        new Address(BUYER),
        new Address(TRAINER));
  }
}
