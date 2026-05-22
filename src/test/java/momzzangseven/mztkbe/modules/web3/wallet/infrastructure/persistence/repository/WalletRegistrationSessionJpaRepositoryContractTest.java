package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.EnumSet;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationStatus;
import momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.entity.WalletRegistrationSessionEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("WalletRegistrationSessionJpaRepository contract test")
class WalletRegistrationSessionJpaRepositoryContractTest {

  @Autowired private WalletRegistrationSessionJpaRepository repository;
  @Autowired private EntityManager entityManager;

  @Test
  void findByPublicIdForUpdate_declaresPessimisticWriteLock() throws NoSuchMethodException {
    Method method =
        WalletRegistrationSessionJpaRepository.class.getMethod(
            "findByPublicIdForUpdate", String.class);

    Lock lock = method.getAnnotation(Lock.class);

    assertThat(lock).isNotNull();
    assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
  }

  @Test
  void existsNewerByUserIdOrWalletAddress_usesAuthoritativeStatusesAndCreatedAtIdTieBreak() {
    LocalDateTime createdAt = LocalDateTime.parse("2026-05-13T10:00:00");
    EnumSet<WalletRegistrationStatus> authoritativeStatuses =
        EnumSet.of(
            WalletRegistrationStatus.APPROVAL_REQUIRED,
            WalletRegistrationStatus.APPROVAL_SIGNED,
            WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN,
            WalletRegistrationStatus.APPROVAL_RETRYABLE,
            WalletRegistrationStatus.FINALIZATION_FAILED,
            WalletRegistrationStatus.LOCAL_CONFLICT,
            WalletRegistrationStatus.REGISTERED);
    WalletRegistrationSessionEntity base =
        repository.saveAndFlush(
            session(
                "registration-base",
                1L,
                "0x" + "a".repeat(40),
                "nonce-base",
                "intent-base",
                WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN,
                createdAt));
    repository.saveAndFlush(
        session(
            "registration-newer-user-failed",
            1L,
            "0x" + "b".repeat(40),
            "nonce-newer-user",
            "intent-newer-user",
            WalletRegistrationStatus.APPROVAL_FAILED,
            createdAt.plusMinutes(1)));
    repository.saveAndFlush(
        session(
            "registration-newer-wallet-expired",
            2L,
            "0x" + "a".repeat(40),
            "nonce-newer-wallet",
            "intent-newer-wallet",
            WalletRegistrationStatus.EXPIRED,
            createdAt.plusMinutes(2)));

    assertThat(
            repository.existsNewerByUserIdOrWalletAddress(
                1L, "0x" + "a".repeat(40), authoritativeStatuses, createdAt, base.getId()))
        .isFalse();

    repository.saveAndFlush(
        session(
            "registration-newer-active",
            1L,
            "0x" + "e".repeat(40),
            "nonce-newer-active",
            "intent-newer-active",
            WalletRegistrationStatus.APPROVAL_REQUIRED,
            createdAt.plusMinutes(3)));

    WalletRegistrationSessionEntity sameTimestampBase =
        repository.saveAndFlush(
            session(
                "registration-same-timestamp-base",
                3L,
                "0x" + "d".repeat(40),
                "nonce-same-timestamp-base",
                "intent-same-timestamp-base",
                WalletRegistrationStatus.CANCELED,
                createdAt));
    WalletRegistrationSessionEntity sameTimestampHigherId =
        repository.saveAndFlush(
            session(
                "registration-same-timestamp-higher",
                3L,
                "0x" + "c".repeat(40),
                "nonce-same-timestamp-higher",
                "intent-same-timestamp-higher",
                WalletRegistrationStatus.CANCELED,
                createdAt));

    assertThat(
            repository.existsNewerByUserIdOrWalletAddress(
                1L, "0x" + "a".repeat(40), authoritativeStatuses, createdAt, base.getId()))
        .isTrue();
    assertThat(
            repository.existsNewerByUserIdOrWalletAddress(
                3L,
                "0x" + "d".repeat(40),
                authoritativeStatuses,
                createdAt,
                sameTimestampBase.getId()))
        .isFalse();
    assertThat(
            repository.existsNewerByUserIdOrWalletAddress(
                3L,
                "0x" + "c".repeat(40),
                authoritativeStatuses,
                createdAt,
                sameTimestampHigherId.getId()))
        .isFalse();

    repository.saveAndFlush(
        session(
            "registration-same-timestamp-active",
            3L,
            "0x" + "e".repeat(40),
            "nonce-same-timestamp-active",
            "intent-same-timestamp-active",
            WalletRegistrationStatus.APPROVAL_REQUIRED,
            createdAt));

    assertThat(
            repository.existsNewerByUserIdOrWalletAddress(
                3L,
                "0x" + "d".repeat(40),
                authoritativeStatuses,
                createdAt,
                sameTimestampBase.getId()))
        .isTrue();
  }

  @Test
  void saveAndReload_persistsReceiptTimeoutExecutionIntentIds() {
    LocalDateTime createdAt = LocalDateTime.parse("2026-05-13T10:00:00");
    WalletRegistrationSessionEntity saved =
        repository.saveAndFlush(
            WalletRegistrationSessionEntity.builder()
                .publicId("registration-timeout-history")
                .userId(10L)
                .walletAddress("0x" + "a".repeat(40))
                .challengeNonce("nonce-timeout-history")
                .status(WalletRegistrationStatus.APPROVAL_RETRYABLE)
                .latestExecutionIntentId("intent-current")
                .receiptTimeoutExecutionIntentIds("intent-timeout-1,intent-timeout-2")
                .retryCount(1)
                .approvalExpiresAt(createdAt.plusMinutes(30))
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build());
    entityManager.clear();

    WalletRegistrationSessionEntity loaded = repository.findById(saved.getId()).orElseThrow();

    assertThat(loaded.getReceiptTimeoutExecutionIntentIds())
        .isEqualTo("intent-timeout-1,intent-timeout-2");
  }

  private static WalletRegistrationSessionEntity session(
      String publicId,
      Long userId,
      String walletAddress,
      String nonce,
      String intentId,
      WalletRegistrationStatus status,
      LocalDateTime createdAt) {
    return WalletRegistrationSessionEntity.builder()
        .publicId(publicId)
        .userId(userId)
        .walletAddress(walletAddress)
        .challengeNonce(nonce)
        .status(status)
        .latestExecutionIntentId(intentId)
        .retryCount(0)
        .approvalExpiresAt(createdAt.plusMinutes(30))
        .createdAt(createdAt)
        .updatedAt(createdAt)
        .build();
  }
}
