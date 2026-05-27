package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.adapter.nonce;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.SponsorNonceLockPort;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
public class SponsorNonceLockPersistenceAdapter implements SponsorNonceLockPort {

  private static final String POSTGRESQL = "PostgreSQL";
  private static final String H2 = "H2";

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void lock(long chainId, String fromAddress) {
    if (chainId <= 0) {
      throw new Web3InvalidInputException("chainId must be positive");
    }
    String normalizedAddress = EvmAddress.of(fromAddress).value();

    jdbcTemplate.execute(
        (ConnectionCallback<Void>)
            connection -> {
              if (!TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new IllegalStateException(
                    "sponsor nonce lock requires an active transaction");
              }
              String productName = connection.getMetaData().getDatabaseProductName();
              upsertLockRow(connection, productName, chainId, normalizedAddress);
              selectLockRowForUpdate(connection, chainId, normalizedAddress);
              return null;
            });
  }

  private void upsertLockRow(
      Connection connection, String productName, long chainId, String fromAddress)
      throws SQLException {
    if (POSTGRESQL.equalsIgnoreCase(productName)) {
      execute(
          connection.prepareStatement(
              """
              INSERT INTO web3_sponsor_nonce_locks(chain_id, from_address, created_at, updated_at)
              VALUES (?, ?, NOW(), NOW())
              ON CONFLICT (chain_id, from_address) DO NOTHING
              """),
          chainId,
          fromAddress);
      return;
    }
    if (H2.equalsIgnoreCase(productName)) {
      execute(
          connection.prepareStatement(
              """
              MERGE INTO web3_sponsor_nonce_locks(chain_id, from_address, created_at, updated_at)
              KEY(chain_id, from_address)
              VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
              """),
          chainId,
          fromAddress);
      return;
    }
    execute(
        connection.prepareStatement(
            """
            INSERT INTO web3_sponsor_nonce_locks(chain_id, from_address, created_at, updated_at)
            VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """),
        chainId,
        fromAddress);
  }

  private void selectLockRowForUpdate(Connection connection, long chainId, String fromAddress)
      throws SQLException {
    execute(
        connection.prepareStatement(
            """
            SELECT chain_id
            FROM web3_sponsor_nonce_locks
            WHERE chain_id = ? AND from_address = ?
            FOR UPDATE
            """),
        chainId,
        fromAddress);
  }

  private void execute(PreparedStatement statement, long chainId, String fromAddress)
      throws SQLException {
    try (statement) {
      statement.setLong(1, chainId);
      statement.setString(2, fromAddress);
      statement.execute();
    }
  }
}
