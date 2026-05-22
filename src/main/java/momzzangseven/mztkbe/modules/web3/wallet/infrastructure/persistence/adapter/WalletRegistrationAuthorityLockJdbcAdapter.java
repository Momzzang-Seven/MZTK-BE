package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.adapter;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.AcquireWalletRegistrationAuthorityLockPort;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** PostgreSQL-backed wallet-registration authority lock with an H2-safe no-op fallback. */
@Component
@RequiredArgsConstructor
public class WalletRegistrationAuthorityLockJdbcAdapter
    implements AcquireWalletRegistrationAuthorityLockPort {

  private static final int USER_NAMESPACE = 0x4D5A5501;
  private static final int WALLET_NAMESPACE = 0x4D5A5701;

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void lock(Long userId, String walletAddress) {
    if (userId == null || walletAddress == null || walletAddress.isBlank()) {
      return;
    }
    jdbcTemplate.execute(
        (ConnectionCallback<Void>)
            connection -> {
              String productName = connection.getMetaData().getDatabaseProductName();
              if (!"PostgreSQL".equalsIgnoreCase(productName)) {
                return null;
              }
              if (!TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new IllegalStateException(
                    "wallet registration authority lock requires an active transaction");
              }
              for (Long lockKey : lockKeys(userId, walletAddress)) {
                acquire(connection.prepareStatement("SELECT pg_advisory_xact_lock(?)"), lockKey);
              }
              return null;
            });
  }

  private static List<Long> lockKeys(Long userId, String walletAddress) {
    return List.of(
            namespacedKey(USER_NAMESPACE, userId.toString()),
            namespacedKey(WALLET_NAMESPACE, walletAddress.toLowerCase()))
        .stream()
        .sorted()
        .toList();
  }

  private static long namespacedKey(int namespace, String value) {
    return ((long) namespace << 32) | (stableHash32(value) & 0xffffffffL);
  }

  private static int stableHash32(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return ByteBuffer.wrap(digest).getInt();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 digest is unavailable", exception);
    }
  }

  private static void acquire(PreparedStatement statement, Long lockKey) throws SQLException {
    try (statement) {
      statement.setLong(1, lockKey);
      statement.execute();
    }
  }
}
