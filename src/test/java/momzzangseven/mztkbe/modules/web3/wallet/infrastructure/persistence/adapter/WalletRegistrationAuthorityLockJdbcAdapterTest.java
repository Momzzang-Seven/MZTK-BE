package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class WalletRegistrationAuthorityLockJdbcAdapterTest {

  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private Connection connection;
  @Mock private DatabaseMetaData metadata;
  @Mock private PreparedStatement firstStatement;
  @Mock private PreparedStatement secondStatement;

  private WalletRegistrationAuthorityLockJdbcAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new WalletRegistrationAuthorityLockJdbcAdapter(jdbcTemplate);
  }

  @AfterEach
  void tearDown() {
    TransactionSynchronizationManager.clear();
  }

  @Test
  void lock_whenDatabaseIsNotPostgresql_doesNothing() throws Exception {
    stubJdbcConnection("H2");

    adapter.lock(7L, "0xABCDEFabcdefABCDEFabcdefABCDEFabcdefabcd");

    verify(connection, never()).prepareStatement("SELECT pg_advisory_xact_lock(?)");
  }

  @Test
  void lock_whenPostgresqlWithoutTransaction_throws() throws Exception {
    stubJdbcConnection("PostgreSQL");

    assertThatThrownBy(() -> adapter.lock(7L, "0xABCDEFabcdefABCDEFabcdefABCDEFabcdefabcd"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("requires an active transaction");
  }

  @Test
  void lock_whenPostgresqlWithTransaction_acquiresUserAndWalletLocksInStableOrder()
      throws Exception {
    stubJdbcConnection("PostgreSQL");
    when(connection.prepareStatement("SELECT pg_advisory_xact_lock(?)"))
        .thenReturn(firstStatement, secondStatement);
    TransactionSynchronizationManager.setActualTransactionActive(true);

    adapter.lock(7L, "0xABCDEFabcdefABCDEFabcdefABCDEFabcdefabcd");

    List<Long> lockKeys = new ArrayList<>();
    captureLockKey(firstStatement, lockKeys);
    captureLockKey(secondStatement, lockKeys);
    assertThat(lockKeys).hasSize(2).isSorted();
    assertThat(lockKeys.get(0)).isNotEqualTo(lockKeys.get(1));
    verify(firstStatement).execute();
    verify(secondStatement).execute();
  }

  @SuppressWarnings("unchecked")
  private void stubJdbcConnection(String productName) throws Exception {
    when(connection.getMetaData()).thenReturn(metadata);
    when(metadata.getDatabaseProductName()).thenReturn(productName);
    when(jdbcTemplate.execute(any(ConnectionCallback.class)))
        .thenAnswer(
            invocation -> {
              ConnectionCallback<Void> callback = invocation.getArgument(0);
              return callback.doInConnection(connection);
            });
  }

  private void captureLockKey(PreparedStatement statement, List<Long> lockKeys) throws Exception {
    ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
    verify(statement).setLong(org.mockito.ArgumentMatchers.eq(1), captor.capture());
    lockKeys.add(captor.getValue());
  }
}
