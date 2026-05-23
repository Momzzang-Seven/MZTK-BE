package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.wallet.application.exception.DuplicateWalletRegistrationSessionException;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationStatus;
import momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.entity.WalletRegistrationSessionEntity;
import momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.repository.WalletRegistrationSessionJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class WalletRegistrationSessionPersistenceAdapterTest {

  private static final LocalDateTime NOW = LocalDateTime.parse("2026-05-13T10:00:00");
  private static final String PUBLIC_ID = "550e8400-e29b-41d4-a716-446655440000";
  private static final String WALLET_ADDRESS = "0x" + "a".repeat(40);

  @Mock private WalletRegistrationSessionJpaRepository repository;

  private WalletRegistrationSessionPersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new WalletRegistrationSessionPersistenceAdapter(repository);
  }

  @Test
  void createAndFlush_mapsEntityToDomain() {
    WalletRegistrationSession session = newSession();
    when(repository.saveAndFlush(any())).thenReturn(entity());

    WalletRegistrationSession saved = adapter.createAndFlush(session);

    assertThat(saved.getPublicId()).isEqualTo(PUBLIC_ID);
    assertThat(saved.getStatus()).isEqualTo(WalletRegistrationStatus.APPROVAL_REQUIRED);
    verify(repository).saveAndFlush(any(WalletRegistrationSessionEntity.class));
  }

  @Test
  void createAndFlush_wrapsUniqueConstraintRace() {
    WalletRegistrationSession session = newSession();
    when(repository.saveAndFlush(any()))
        .thenThrow(new DataIntegrityViolationException("duplicate"));

    assertThatThrownBy(() -> adapter.createAndFlush(session))
        .isInstanceOf(DuplicateWalletRegistrationSessionException.class)
        .hasMessage("duplicate wallet registration session");
  }

  @Test
  void createAndFlush_participatesInCallerTransactionForAtomicRegistration() throws Exception {
    Transactional transactional =
        WalletRegistrationSessionPersistenceAdapter.class
            .getMethod("createAndFlush", WalletRegistrationSession.class)
            .getAnnotation(Transactional.class);

    assertThat(transactional).isNotNull();
    assertThat(transactional.readOnly()).isFalse();
  }

  @Test
  void loadLatestNonTerminalByUserId_usesNonTerminalStatusesAndLatestLimit() {
    when(repository.findLatestByUserIdAndStatusIn(eq(1L), any(), any(Pageable.class)))
        .thenReturn(List.of(entity()));

    Optional<WalletRegistrationSession> loaded = adapter.loadLatestNonTerminalByUserId(1L);

    assertThat(loaded).isPresent();
    assertThat(loaded.get().getUserId()).isEqualTo(1L);
    ArgumentCaptor<Collection<WalletRegistrationStatus>> statusesCaptor =
        ArgumentCaptor.forClass(Collection.class);
    verify(repository)
        .findLatestByUserIdAndStatusIn(eq(1L), statusesCaptor.capture(), any(Pageable.class));
    assertThat(statusesCaptor.getValue())
        .containsExactlyInAnyOrder(
            WalletRegistrationStatus.APPROVAL_REQUIRED,
            WalletRegistrationStatus.APPROVAL_SIGNED,
            WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN,
            WalletRegistrationStatus.APPROVAL_RETRYABLE,
            WalletRegistrationStatus.FINALIZATION_FAILED,
            WalletRegistrationStatus.LOCAL_CONFLICT);
    assertThat(statusesCaptor.getValue()).doesNotContain(WalletRegistrationStatus.APPROVAL_FAILED);
  }

  @Test
  void loadByPublicIdAndUserId_mapsOwnerBoundRead() {
    when(repository.findByPublicIdAndUserId(PUBLIC_ID, 1L)).thenReturn(Optional.of(entity()));

    Optional<WalletRegistrationSession> loaded = adapter.loadByPublicIdAndUserId(PUBLIC_ID, 1L);

    assertThat(loaded).isPresent();
    assertThat(loaded.get().getWalletAddress()).isEqualTo(WALLET_ADDRESS);
    assertThat(loaded.get().hasReceiptTimeoutExecutionIntent("intent-timeout")).isTrue();
  }

  @Test
  void existsNewerByUserIdOrWalletAddress_delegatesToRepositoryQueryWithAuthoritativeStatuses() {
    when(repository.existsNewerByUserIdOrWalletAddress(eq(1L), eq(WALLET_ADDRESS), any(), eq(10L)))
        .thenReturn(true);

    boolean exists = adapter.existsNewerByUserIdOrWalletAddress(1L, WALLET_ADDRESS, 10L);

    assertThat(exists).isTrue();
    ArgumentCaptor<Collection<WalletRegistrationStatus>> statusesCaptor =
        ArgumentCaptor.forClass(Collection.class);
    verify(repository)
        .existsNewerByUserIdOrWalletAddress(
            eq(1L), eq(WALLET_ADDRESS), statusesCaptor.capture(), eq(10L));
    assertThat(statusesCaptor.getValue())
        .containsExactlyInAnyOrder(
            WalletRegistrationStatus.APPROVAL_REQUIRED,
            WalletRegistrationStatus.APPROVAL_SIGNED,
            WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN,
            WalletRegistrationStatus.APPROVAL_RETRYABLE,
            WalletRegistrationStatus.FINALIZATION_FAILED,
            WalletRegistrationStatus.LOCAL_CONFLICT,
            WalletRegistrationStatus.REGISTERED);
  }

  @Test
  void existsNewerByUserIdOrWalletAddress_whenRequiredInputMissing_returnsFalse() {
    boolean exists = adapter.existsNewerByUserIdOrWalletAddress(null, WALLET_ADDRESS, 10L);

    assertThat(exists).isFalse();
  }

  @Test
  void lockByPublicIdForUpdate_delegatesToRepositoryLockMethod() {
    when(repository.findByPublicIdForUpdate(PUBLIC_ID)).thenReturn(Optional.of(entity()));

    Optional<WalletRegistrationSession> locked = adapter.lockByPublicIdForUpdate(PUBLIC_ID);

    assertThat(locked).isPresent();
    verify(repository).findByPublicIdForUpdate(PUBLIC_ID);
  }

  @Test
  void loadRecoveryCandidates_usesRecoveryStatusesAndReceiptTimeoutFailedTarget() {
    when(repository.findRecoveryCandidates(any(), any(), any(), any(Pageable.class)))
        .thenReturn(List.of(entity()));

    List<WalletRegistrationSession> loaded = adapter.loadRecoveryCandidates(50);

    assertThat(loaded).hasSize(1);
    ArgumentCaptor<Collection<WalletRegistrationStatus>> statusesCaptor =
        ArgumentCaptor.forClass(Collection.class);
    ArgumentCaptor<WalletRegistrationStatus> receiptTimeoutStatusCaptor =
        ArgumentCaptor.forClass(WalletRegistrationStatus.class);
    ArgumentCaptor<String> receiptTimeoutErrorCodeCaptor = ArgumentCaptor.forClass(String.class);
    verify(repository)
        .findRecoveryCandidates(
            statusesCaptor.capture(),
            receiptTimeoutStatusCaptor.capture(),
            receiptTimeoutErrorCodeCaptor.capture(),
            any(Pageable.class));
    assertThat(statusesCaptor.getValue())
        .containsExactlyInAnyOrder(
            WalletRegistrationStatus.APPROVAL_REQUIRED,
            WalletRegistrationStatus.APPROVAL_SIGNED,
            WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN,
            WalletRegistrationStatus.APPROVAL_RETRYABLE,
            WalletRegistrationStatus.FINALIZATION_FAILED,
            WalletRegistrationStatus.LOCAL_CONFLICT);
    assertThat(statusesCaptor.getValue()).doesNotContain(WalletRegistrationStatus.APPROVAL_FAILED);
    assertThat(receiptTimeoutStatusCaptor.getValue())
        .isEqualTo(WalletRegistrationStatus.APPROVAL_FAILED);
    assertThat(receiptTimeoutErrorCodeCaptor.getValue()).isEqualTo("RECEIPT_TIMEOUT");
  }

  @Test
  void advanceReceiptTimeoutFailedRecoveryCursor_delegatesConditionedCursorUpdate() {
    adapter.advanceReceiptTimeoutFailedRecoveryCursor(PUBLIC_ID, NOW);

    verify(repository)
        .advanceRecoveryCursor(
            PUBLIC_ID, WalletRegistrationStatus.APPROVAL_FAILED, "RECEIPT_TIMEOUT", NOW);
  }

  private static WalletRegistrationSession newSession() {
    return WalletRegistrationSession.create(
        PUBLIC_ID, 1L, WALLET_ADDRESS, "challenge-nonce", NOW.plusMinutes(30), NOW);
  }

  private static WalletRegistrationSessionEntity entity() {
    return WalletRegistrationSessionEntity.builder()
        .id(10L)
        .publicId(PUBLIC_ID)
        .userId(1L)
        .walletAddress(WALLET_ADDRESS)
        .challengeNonce("challenge-nonce")
        .status(WalletRegistrationStatus.APPROVAL_REQUIRED)
        .receiptTimeoutExecutionIntentIds("intent-timeout")
        .retryCount(0)
        .approvalExpiresAt(NOW.plusMinutes(30))
        .createdAt(NOW)
        .updatedAt(NOW)
        .build();
  }
}
