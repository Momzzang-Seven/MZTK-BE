package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.persistence.EntityManager;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Profile;

/**
 * Unit tests for {@link PostgresTreasuryAdvisoryLockAdapter} — covers [M-102], [M-103], [M-104].
 *
 * <p>The actual {@code pg_advisory_xact_lock} round-trip belongs to E2E; here only the pure-logic
 * guards (blank-address rejection, no-active-transaction rejection) and the profile annotation are
 * exercised.
 */
@ExtendWith(MockitoExtension.class)
class PostgresTreasuryAdvisoryLockAdapterTest {

  @Mock private EntityManager entityManager;

  @InjectMocks private PostgresTreasuryAdvisoryLockAdapter adapter;

  @Test
  void lockForAddress_rejectsBlankAddress_withoutTouchingEntityManager() {
    assertThatThrownBy(() -> adapter.lockForAddress(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("walletAddress");
    assertThatThrownBy(() -> adapter.lockForAddress(""))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("walletAddress");
    assertThatThrownBy(() -> adapter.lockForAddress("   "))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("walletAddress");
    verifyNoInteractions(entityManager);
  }

  @Test
  void lockForAddress_rejectsCallOutsideActiveTransaction() {
    assertThatThrownBy(() -> adapter.lockForAddress("0x" + "a".repeat(40)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("active transaction");
    verifyNoInteractions(entityManager);
  }

  @Test
  void isAnnotatedWithNonTestProfile() {
    Profile profile = PostgresTreasuryAdvisoryLockAdapter.class.getAnnotation(Profile.class);
    assertThat(profile).isNotNull();
    assertThat(profile.value()).containsExactly("!test");
  }
}
