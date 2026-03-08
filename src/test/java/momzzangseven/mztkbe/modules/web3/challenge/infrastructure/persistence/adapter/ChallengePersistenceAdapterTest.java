package momzzangseven.mztkbe.modules.web3.challenge.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.challenge.domain.model.Challenge;
import momzzangseven.mztkbe.modules.web3.challenge.domain.model.ChallengePurpose;
import momzzangseven.mztkbe.modules.web3.challenge.domain.model.ChallengeStatus;
import momzzangseven.mztkbe.modules.web3.challenge.infrastructure.entity.ChallengeEntity;
import momzzangseven.mztkbe.modules.web3.challenge.infrastructure.persistence.repository.ChallengeJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChallengePersistenceAdapterTest {

  @Mock private ChallengeJpaRepository repository;

  private ChallengePersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new ChallengePersistenceAdapter(repository);
  }

  @Test
  void save_mapsDomainToEntityAndBack() {
    Challenge challenge =
        Challenge.builder()
            .nonce("nonce-1")
            .userId(1L)
            .purpose(ChallengePurpose.WALLET_REGISTRATION)
            .walletAddress("0x" + "a".repeat(40))
            .message("siwe")
            .status(ChallengeStatus.PENDING)
            .expiresAt(Instant.now().plusSeconds(60))
            .createdAt(Instant.now())
            .build();

    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    Challenge saved = adapter.save(challenge);

    ArgumentCaptor<ChallengeEntity> captor = ArgumentCaptor.forClass(ChallengeEntity.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getNonce()).isEqualTo("nonce-1");
    assertThat(saved.getNonce()).isEqualTo("nonce-1");
    assertThat(saved.getPurpose()).isEqualTo(ChallengePurpose.WALLET_REGISTRATION);
  }

  @Test
  void findByNonce_mapsEntityToDomain() {
    ChallengeEntity entity =
        ChallengeEntity.builder()
            .nonce("nonce-2")
            .userId(2L)
            .purpose(ChallengePurpose.WALLET_REGISTRATION)
            .walletAddress("0x" + "b".repeat(40))
            .message("msg")
            .status(ChallengeStatus.USED)
            .expiresAt(Instant.now().plusSeconds(120))
            .createdAt(Instant.now())
            .usedAt(Instant.now())
            .build();
    when(repository.findByNonce("nonce-2")).thenReturn(Optional.of(entity));

    Optional<Challenge> result = adapter.findByNonce("nonce-2");

    assertThat(result).isPresent();
    assertThat(result.get().getStatus()).isEqualTo(ChallengeStatus.USED);
  }
}
