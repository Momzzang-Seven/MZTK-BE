package momzzangseven.mztkbe.modules.web3.transaction.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import javax.sql.DataSource;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.PersistSponsorNonceTransactionStateUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.nonce.ManageNonceSlotLifecycleUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.MarkExecutionIntentPendingOnchainPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.LoadSponsorNonceSlotsPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.RecordSponsorNonceEvidencePort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.RecordSponsorNonceSlotTransitionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.ReserveSponsorNonceSlotPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.VerifyUnbroadcastableAttemptPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.service.nonce.NonceSlotLifecycleService;
import momzzangseven.mztkbe.modules.web3.transaction.domain.event.Web3TransactionSucceededEvent;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.SponsorNonceProperties;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.TransactionRewardTokenProperties;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.adapter.nonce.NonceSlotPersistenceAdapter;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.Web3TransactionJpaRepository;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.nonce.NonceSlotAttemptJpaRepository;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.nonce.NonceSlotEvidenceJpaRepository;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.nonce.NonceSlotJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringJUnitConfig(NonceSlotMissingTransactionRollbackPolicyTest.Config.class)
class NonceSlotMissingTransactionRollbackPolicyTest {

  private static final long CHAIN_ID = 84532L;
  private static final String FROM_ADDRESS = "0x" + "a".repeat(40);
  private static final long NONCE = 48L;
  private static final long TX_ID = 43L;
  private static final String TX_HASH =
      "0x62039b6af312ea72114d7e635069045569bc05cad3f5a6180800e8a7bd13a323";
  private static final LocalDateTime NOW = LocalDateTime.parse("2026-05-27T22:00:31");

  @Autowired private PersistSponsorNonceTransactionStateUseCase persistService;
  @Autowired private TransactionOutcomePublisher outcomePublisher;
  @Autowired private NonceSlotJpaRepository slotRepository;
  @Autowired private UpdateTransactionPort updateTransactionPort;
  @Autowired private ApplicationEventPublisher eventPublisher;

  @AfterEach
  void tearDown() {
    reset(slotRepository, updateTransactionPort, eventPublisher);
  }

  @Test
  void markUnconfirmed_commitsTransactionStatusWhenNonceSlotIsMissing() {
    when(slotRepository.findByScopeForUpdate(CHAIN_ID, FROM_ADDRESS, NONCE))
        .thenReturn(Optional.empty());

    assertThatCode(
            () ->
                persistService.markUnconfirmed(
                    new PersistSponsorNonceTransactionStateUseCase.SponsorNonceUnconfirmedCommand(
                        TX_ID,
                        CHAIN_ID,
                        FROM_ADDRESS,
                        NONCE,
                        TX_HASH,
                        "RECEIPT_TIMEOUT_900S",
                        NOW)))
        .doesNotThrowAnyException();

    verify(updateTransactionPort)
        .updateStatus(TX_ID, Web3TxStatus.UNCONFIRMED, TX_HASH, "RECEIPT_TIMEOUT_900S");
  }

  @Test
  void markSucceededWithNonceSlotAndPublish_commitsSuccessWhenNonceSlotIsMissing() {
    when(slotRepository.findByScopeForUpdate(CHAIN_ID, FROM_ADDRESS, NONCE))
        .thenReturn(Optional.empty());

    assertThatCode(
            () ->
                outcomePublisher.markSucceededWithNonceSlotAndPublish(
                    TX_ID,
                    "qna-answer-submit:18790",
                    Web3ReferenceType.USER_TO_SERVER,
                    "18790",
                    1L,
                    2L,
                    TX_HASH,
                    new TransactionOutcomePublisher.SponsorNonceReceiptCommand(
                        CHAIN_ID, FROM_ADDRESS, NONCE, "RECEIPT_STATUS_1", NOW)))
        .doesNotThrowAnyException();

    verify(updateTransactionPort).updateStatus(TX_ID, Web3TxStatus.SUCCEEDED, TX_HASH, null);
    verify(eventPublisher).publishEvent(any(Web3TransactionSucceededEvent.class));
  }

  @Configuration
  @EnableTransactionManagement(proxyTargetClass = true)
  static class Config {

    @Bean
    DataSource dataSource() {
      return new EmbeddedDatabaseBuilder()
          .generateUniqueName(true)
          .setType(EmbeddedDatabaseType.H2)
          .build();
    }

    @Bean
    PlatformTransactionManager transactionManager(DataSource dataSource) {
      return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    NonceSlotJpaRepository slotRepository() {
      return mock(NonceSlotJpaRepository.class);
    }

    @Bean
    NonceSlotAttemptJpaRepository attemptRepository() {
      return mock(NonceSlotAttemptJpaRepository.class);
    }

    @Bean
    NonceSlotEvidenceJpaRepository evidenceRepository() {
      return mock(NonceSlotEvidenceJpaRepository.class);
    }

    @Bean
    Web3TransactionJpaRepository transactionRepository() {
      return mock(Web3TransactionJpaRepository.class);
    }

    @Bean
    TransactionRewardTokenProperties transactionRewardTokenProperties() {
      return new TransactionRewardTokenProperties();
    }

    @Bean
    SponsorNonceProperties sponsorNonceProperties() {
      return new SponsorNonceProperties();
    }

    @Bean
    Clock appClock() {
      return Clock.fixed(Instant.parse("2026-05-27T13:00:31Z"), ZoneId.of("Asia/Seoul"));
    }

    @Bean
    NonceSlotPersistenceAdapter nonceSlotPersistenceAdapter(
        NonceSlotJpaRepository slotRepository,
        NonceSlotAttemptJpaRepository attemptRepository,
        NonceSlotEvidenceJpaRepository evidenceRepository,
        Web3TransactionJpaRepository transactionRepository,
        TransactionRewardTokenProperties transactionRewardTokenProperties,
        SponsorNonceProperties sponsorNonceProperties,
        Clock appClock) {
      return new NonceSlotPersistenceAdapter(
          slotRepository,
          attemptRepository,
          evidenceRepository,
          transactionRepository,
          transactionRewardTokenProperties,
          sponsorNonceProperties,
          appClock);
    }

    @Bean
    NonceSlotLifecycleService nonceSlotLifecycleService(
        ReserveSponsorNonceSlotPort reserveSponsorNonceSlotPort,
        RecordSponsorNonceSlotTransitionPort recordSponsorNonceSlotTransitionPort,
        RecordSponsorNonceEvidencePort recordSponsorNonceEvidencePort,
        VerifyUnbroadcastableAttemptPort verifyUnbroadcastableAttemptPort,
        LoadSponsorNonceSlotsPort loadSponsorNonceSlotsPort) {
      return new NonceSlotLifecycleService(
          reserveSponsorNonceSlotPort,
          recordSponsorNonceSlotTransitionPort,
          recordSponsorNonceEvidencePort,
          verifyUnbroadcastableAttemptPort,
          loadSponsorNonceSlotsPort);
    }

    @Bean
    UpdateTransactionPort updateTransactionPort() {
      return mock(UpdateTransactionPort.class);
    }

    @Bean
    MarkExecutionIntentPendingOnchainPort markExecutionIntentPendingOnchainPort() {
      return mock(MarkExecutionIntentPendingOnchainPort.class);
    }

    @Bean
    PersistSponsorNonceTransactionStateService persistSponsorNonceTransactionStateService(
        UpdateTransactionPort updateTransactionPort,
        ManageNonceSlotLifecycleUseCase nonceSlotLifecycleUseCase,
        MarkExecutionIntentPendingOnchainPort markExecutionIntentPendingOnchainPort) {
      return new PersistSponsorNonceTransactionStateService(
          updateTransactionPort, nonceSlotLifecycleUseCase, markExecutionIntentPendingOnchainPort);
    }

    @Bean
    ApplicationEventPublisher eventPublisher() {
      return mock(ApplicationEventPublisher.class);
    }

    @Bean
    TransactionOutcomePublisher transactionOutcomePublisher(
        UpdateTransactionPort updateTransactionPort,
        ManageNonceSlotLifecycleUseCase nonceSlotLifecycleUseCase,
        ApplicationEventPublisher eventPublisher) {
      return new TransactionOutcomePublisher(
          updateTransactionPort, nonceSlotLifecycleUseCase, eventPublisher);
    }
  }
}
