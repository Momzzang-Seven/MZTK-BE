package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.RunWalletRegistrationTransactionPort;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class WalletRegistrationRetryTransactionConfigTest {

  @Test
  void runWalletRegistrationTransactionPort_usesRequiresNewSoInnerRollbackDoesNotPoisonOuter() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            "jdbc:h2:mem:wallet_registration_tx_config;DB_CLOSE_DELAY=-1", "sa", "");
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("create table tx_guard (id integer primary key)");
    DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
    RunWalletRegistrationTransactionPort registrationTransactionPort =
        new WalletRegistrationRetryTransactionConfig()
            .runWalletRegistrationTransactionPort(transactionManager);
    TransactionTemplate outerTransaction = new TransactionTemplate(transactionManager);

    outerTransaction.executeWithoutResult(
        status -> {
          assertThatThrownBy(
                  () ->
                      registrationTransactionPort.execute(
                          () -> {
                            jdbcTemplate.update("insert into tx_guard(id) values (1)");
                            jdbcTemplate.update("insert into tx_guard(id) values (1)");
                            return null;
                          }))
              .isInstanceOf(RuntimeException.class);
          jdbcTemplate.update("insert into tx_guard(id) values (2)");
        });

    Integer committedRows =
        jdbcTemplate.queryForObject("select count(*) from tx_guard where id = 2", Integer.class);
    Integer rolledBackRows =
        jdbcTemplate.queryForObject("select count(*) from tx_guard where id = 1", Integer.class);
    assertThat(committedRows).isOne();
    assertThat(rolledBackRows).isZero();
  }
}
