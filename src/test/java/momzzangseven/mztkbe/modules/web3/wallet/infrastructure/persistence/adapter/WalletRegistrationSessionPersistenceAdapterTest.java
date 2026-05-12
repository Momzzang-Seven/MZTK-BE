package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
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
  }

  @Test
  void loadByPublicIdAndUserId_mapsOwnerBoundRead() {
    when(repository.findByPublicIdAndUserId(PUBLIC_ID, 1L)).thenReturn(Optional.of(entity()));

    Optional<WalletRegistrationSession> loaded = adapter.loadByPublicIdAndUserId(PUBLIC_ID, 1L);

    assertThat(loaded).isPresent();
    assertThat(loaded.get().getWalletAddress()).isEqualTo(WALLET_ADDRESS);
  }

  @Test
  void lockByPublicIdForUpdate_delegatesToRepositoryLockMethod() {
    when(repository.findByPublicIdForUpdate(PUBLIC_ID)).thenReturn(Optional.of(entity()));

    Optional<WalletRegistrationSession> locked = adapter.lockByPublicIdForUpdate(PUBLIC_ID);

    assertThat(locked).isPresent();
    verify(repository).findByPublicIdForUpdate(PUBLIC_ID);
  }

  @Test
  void loadRecoveryCandidates_usesNonTerminalStatusesAndLimit() {
    when(repository.findByStatusInOrderByUpdatedAtAscIdAsc(any(), any(Pageable.class)))
        .thenReturn(List.of(entity()));

    List<WalletRegistrationSession> loaded = adapter.loadRecoveryCandidates(50);

    assertThat(loaded).hasSize(1);
    verify(repository).findByStatusInOrderByUpdatedAtAscIdAsc(any(), any(Pageable.class));
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
        .retryCount(0)
        .approvalExpiresAt(NOW.plusMinutes(30))
        .createdAt(NOW)
        .updatedAt(NOW)
        .build();
  }
}
