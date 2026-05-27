package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.adapter.nonce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
class SponsorNonceLockPersistenceAdapterTest {

  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private Connection connection;
  @Mock private DatabaseMetaData metadata;
  @Mock private PreparedStatement insertStatement;
  @Mock private PreparedStatement selectForUpdateStatement;

  private SponsorNonceLockPersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new SponsorNonceLockPersistenceAdapter(jdbcTemplate);
  }

  @AfterEach
  void tearDown() {
    TransactionSynchronizationManager.clear();
  }

  @Test
  void lock_whenPostgresqlWithTransaction_upsertsThenSelectsExactScopeForUpdate() throws Exception {
    stubJdbcConnection("PostgreSQL");
    when(connection.prepareStatement(anyString()))
        .thenReturn(insertStatement, selectForUpdateStatement);
    TransactionSynchronizationManager.setActualTransactionActive(true);

    adapter.lock(84532L, "0xABCDEFabcdefABCDEFabcdefABCDEFabcdefabcd");

    List<String> sql = capturedSql();
    assertThat(sql.get(0)).contains("ON CONFLICT");
    assertThat(sql.get(1)).contains("FOR UPDATE");
    verify(insertStatement).setLong(1, 84532L);
    verify(insertStatement).setString(2, "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd");
    verify(selectForUpdateStatement).setLong(1, 84532L);
    verify(selectForUpdateStatement).setString(2, "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd");
  }

  @Test
  void lock_whenH2WithTransaction_usesMergeFallbackThenSelectForUpdate() throws Exception {
    stubJdbcConnection("H2");
    when(connection.prepareStatement(anyString()))
        .thenReturn(insertStatement, selectForUpdateStatement);
    TransactionSynchronizationManager.setActualTransactionActive(true);

    adapter.lock(84532L, "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd");

    List<String> sql = capturedSql();
    assertThat(sql.get(0)).contains("MERGE INTO web3_sponsor_nonce_locks");
    assertThat(sql.get(1)).contains("FOR UPDATE");
  }

  @Test
  void lock_whenNoTransaction_throwsBeforeLocking() throws Exception {
    stubJdbcConnectionWithoutMetadata();

    assertThatThrownBy(() -> adapter.lock(84532L, "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("requires an active transaction");
  }

  @SuppressWarnings("unchecked")
  private void stubJdbcConnectionWithoutMetadata() {
    when(jdbcTemplate.execute(any(ConnectionCallback.class)))
        .thenAnswer(
            invocation -> {
              ConnectionCallback<Void> callback = invocation.getArgument(0);
              return callback.doInConnection(connection);
            });
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

  private List<String> capturedSql() throws Exception {
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(connection, org.mockito.Mockito.times(2)).prepareStatement(captor.capture());
    return new ArrayList<>(captor.getAllValues());
  }
}
