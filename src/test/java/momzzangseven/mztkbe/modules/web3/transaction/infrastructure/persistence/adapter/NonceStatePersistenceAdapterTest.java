package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.Web3NonceStateEntity;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.Web3NonceStateJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;

@ExtendWith(MockitoExtension.class)
class NonceStatePersistenceAdapterTest {

  @Mock private Web3NonceStateJpaRepository repository;
  @Mock private Web3CoreProperties web3CoreProperties;

  private NonceStatePersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new NonceStatePersistenceAdapter(repository, web3CoreProperties);
  }

  @Test
  void reserveNextNonce_throws_whenAddressBlank() {
    assertThatThrownBy(() -> adapter.reserveNextNonce(" "))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("fromAddress is required");
  }

  @Test
  void reserveNextNonce_throws_whenAddressNull() {
    assertThatThrownBy(() -> adapter.reserveNextNonce(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("fromAddress is required");
  }

  @Test
  void reserveNextNonce_returnsAndIncrementsExistingNonce() {
    Web3NonceStateEntity entity =
        Web3NonceStateEntity.builder().fromAddress("0x" + "a".repeat(40)).nextNonce(5L).build();
    when(repository.findByFromAddressForUpdate("0x" + "a".repeat(40)))
        .thenReturn(Optional.of(entity));

    long reserved = adapter.reserveNextNonce("0x" + "A".repeat(40));

    assertThat(reserved).isEqualTo(5L);
    assertThat(entity.getNextNonce()).isEqualTo(6L);
    verify(repository).save(entity);
  }

  @Test
  void reserveNextNonce_initializesState_whenMissing() {
    when(repository.findByFromAddressForUpdate("0x" + "b".repeat(40))).thenReturn(Optional.empty());

    long reserved = adapter.reserveNextNonce("0x" + "B".repeat(40));

    ArgumentCaptor<Web3NonceStateEntity> captor =
        ArgumentCaptor.forClass(Web3NonceStateEntity.class);
    verify(repository).save(captor.capture());
    assertThat(reserved).isEqualTo(0L);
    assertThat(captor.getValue().getFromAddress()).isEqualTo("0x" + "b".repeat(40));
    assertThat(captor.getValue().getNextNonce()).isEqualTo(1L);
  }

  @Test
  void reserveNextNonce_syncsWithOnchainPendingNonce_whenOnchainAhead() throws Exception {
    Web3NonceStateEntity entity =
        Web3NonceStateEntity.builder().fromAddress("0x" + "a".repeat(40)).nextNonce(5L).build();
    when(repository.findByFromAddressForUpdate("0x" + "a".repeat(40)))
        .thenReturn(Optional.of(entity));

    Web3j mainWeb3j = mock(Web3j.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    EthGetTransactionCount response = new EthGetTransactionCount();
    response.setResult("0xa");
    when(mainWeb3j.ethGetTransactionCount("0x" + "a".repeat(40), DefaultBlockParameterName.PENDING).send())
        .thenReturn(response);
    ReflectionTestUtils.setField(adapter, "mainWeb3j", mainWeb3j);

    long reserved = adapter.reserveNextNonce("0x" + "a".repeat(40));

    assertThat(reserved).isEqualTo(10L);
    assertThat(entity.getNextNonce()).isEqualTo(11L);
    verify(repository).save(entity);
  }

  @Test
  void reserveNextNonce_usesSubRpcPendingNonce_whenMainReturnsError() throws Exception {
    Web3NonceStateEntity entity =
        Web3NonceStateEntity.builder().fromAddress("0x" + "a".repeat(40)).nextNonce(1L).build();
    when(repository.findByFromAddressForUpdate("0x" + "a".repeat(40)))
        .thenReturn(Optional.of(entity));

    Web3j mainWeb3j = mock(Web3j.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    EthGetTransactionCount mainResponse = new EthGetTransactionCount();
    mainResponse.setError(new Response.Error(1, "main error"));
    when(mainWeb3j.ethGetTransactionCount("0x" + "a".repeat(40), DefaultBlockParameterName.PENDING).send())
        .thenReturn(mainResponse);

    Web3j subWeb3j = mock(Web3j.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    EthGetTransactionCount subResponse = new EthGetTransactionCount();
    subResponse.setResult("0x9");
    when(subWeb3j.ethGetTransactionCount("0x" + "a".repeat(40), DefaultBlockParameterName.PENDING).send())
        .thenReturn(subResponse);

    ReflectionTestUtils.setField(adapter, "mainWeb3j", mainWeb3j);
    ReflectionTestUtils.setField(adapter, "subWeb3j", subWeb3j);

    long reserved = adapter.reserveNextNonce("0x" + "a".repeat(40));

    assertThat(reserved).isEqualTo(9L);
    assertThat(entity.getNextNonce()).isEqualTo(10L);
  }

  @Test
  void reserveNextNonce_keepsDbSequence_whenOnchainPendingNonceNotAhead() throws Exception {
    Web3NonceStateEntity entity =
        Web3NonceStateEntity.builder().fromAddress("0x" + "a".repeat(40)).nextNonce(8L).build();
    when(repository.findByFromAddressForUpdate("0x" + "a".repeat(40)))
        .thenReturn(Optional.of(entity));

    Web3j mainWeb3j = mock(Web3j.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    EthGetTransactionCount response = new EthGetTransactionCount();
    response.setResult("0x5");
    when(mainWeb3j.ethGetTransactionCount("0x" + "a".repeat(40), DefaultBlockParameterName.PENDING).send())
        .thenReturn(response);
    ReflectionTestUtils.setField(adapter, "mainWeb3j", mainWeb3j);

    long reserved = adapter.reserveNextNonce("0x" + "a".repeat(40));

    assertThat(reserved).isEqualTo(8L);
    assertThat(entity.getNextNonce()).isEqualTo(9L);
  }

  @Test
  void reserveNextNonce_ignoresNegativePendingNonce_andKeepsDbSequence() throws Exception {
    Web3NonceStateEntity entity =
        Web3NonceStateEntity.builder().fromAddress("0x" + "a".repeat(40)).nextNonce(3L).build();
    when(repository.findByFromAddressForUpdate("0x" + "a".repeat(40)))
        .thenReturn(Optional.of(entity));

    Web3j mainWeb3j = mock(Web3j.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    EthGetTransactionCount response =
        new EthGetTransactionCount() {
          @Override
          public BigInteger getTransactionCount() {
            return BigInteger.valueOf(-1);
          }
        };
    when(mainWeb3j.ethGetTransactionCount("0x" + "a".repeat(40), DefaultBlockParameterName.PENDING).send())
        .thenReturn(response);
    ReflectionTestUtils.setField(adapter, "mainWeb3j", mainWeb3j);

    long reserved = adapter.reserveNextNonce("0x" + "a".repeat(40));

    assertThat(reserved).isEqualTo(3L);
    assertThat(entity.getNextNonce()).isEqualTo(4L);
  }

  @Test
  void reserveNextNonce_keepsDbSequence_whenMainRpcThrowsException() throws Exception {
    Web3NonceStateEntity entity =
        Web3NonceStateEntity.builder().fromAddress("0x" + "a".repeat(40)).nextNonce(4L).build();
    when(repository.findByFromAddressForUpdate("0x" + "a".repeat(40)))
        .thenReturn(Optional.of(entity));

    Web3j mainWeb3j = mock(Web3j.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    when(mainWeb3j.ethGetTransactionCount("0x" + "a".repeat(40), DefaultBlockParameterName.PENDING).send())
        .thenThrow(new RuntimeException("rpc boom"));
    ReflectionTestUtils.setField(adapter, "mainWeb3j", mainWeb3j);

    long reserved = adapter.reserveNextNonce("0x" + "a".repeat(40));

    assertThat(reserved).isEqualTo(4L);
    assertThat(entity.getNextNonce()).isEqualTo(5L);
  }

  @Test
  void reserveNextNonce_keepsDbSequence_whenMainRpcReturnsNullResponse() throws Exception {
    Web3NonceStateEntity entity =
        Web3NonceStateEntity.builder().fromAddress("0x" + "a".repeat(40)).nextNonce(6L).build();
    when(repository.findByFromAddressForUpdate("0x" + "a".repeat(40)))
        .thenReturn(Optional.of(entity));

    Web3j mainWeb3j = mock(Web3j.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    when(mainWeb3j.ethGetTransactionCount("0x" + "a".repeat(40), DefaultBlockParameterName.PENDING).send())
        .thenReturn(null);
    ReflectionTestUtils.setField(adapter, "mainWeb3j", mainWeb3j);

    long reserved = adapter.reserveNextNonce("0x" + "a".repeat(40));

    assertThat(reserved).isEqualTo(6L);
    assertThat(entity.getNextNonce()).isEqualTo(7L);
  }

  @Test
  void reserveNextNonce_ignoresOverflowPendingNonce_andKeepsDbSequence() throws Exception {
    Web3NonceStateEntity entity =
        Web3NonceStateEntity.builder().fromAddress("0x" + "a".repeat(40)).nextNonce(3L).build();
    when(repository.findByFromAddressForUpdate("0x" + "a".repeat(40)))
        .thenReturn(Optional.of(entity));

    Web3j mainWeb3j = mock(Web3j.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    EthGetTransactionCount response = new EthGetTransactionCount();
    response.setResult(NumericHex.toQuantityHex(BigInteger.ONE.shiftLeft(80)));
    when(mainWeb3j.ethGetTransactionCount("0x" + "a".repeat(40), DefaultBlockParameterName.PENDING).send())
        .thenReturn(response);
    ReflectionTestUtils.setField(adapter, "mainWeb3j", mainWeb3j);

    long reserved = adapter.reserveNextNonce("0x" + "a".repeat(40));

    assertThat(reserved).isEqualTo(3L);
    assertThat(entity.getNextNonce()).isEqualTo(4L);
  }

  @Test
  void shutdown_closesBothClients_whenInitialized() {
    Web3j mainWeb3j = mock(Web3j.class);
    Web3j subWeb3j = mock(Web3j.class);
    ReflectionTestUtils.setField(adapter, "mainWeb3j", mainWeb3j);
    ReflectionTestUtils.setField(adapter, "subWeb3j", subWeb3j);

    adapter.shutdown();

    verify(mainWeb3j).shutdown();
    verify(subWeb3j).shutdown();
  }

  @Test
  void shutdown_doesNothing_whenClientsNotInitialized() {
    adapter.shutdown();

    assertThat(adapter).isNotNull();
  }

  private static final class NumericHex {
    private NumericHex() {}

    private static String toQuantityHex(BigInteger value) {
      return "0x" + value.toString(16);
    }
  }
}
