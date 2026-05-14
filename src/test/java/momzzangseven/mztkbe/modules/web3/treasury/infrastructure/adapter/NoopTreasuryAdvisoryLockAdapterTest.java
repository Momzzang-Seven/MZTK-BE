package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

/** Unit tests for {@link NoopTreasuryAdvisoryLockAdapter} — covers [M-100], [M-101]. */
class NoopTreasuryAdvisoryLockAdapterTest {

  private final NoopTreasuryAdvisoryLockAdapter adapter = new NoopTreasuryAdvisoryLockAdapter();

  @Test
  void lockForAddress_isNoOp_andNeverThrows() {
    assertThatCode(() -> adapter.lockForAddress("0x" + "a".repeat(40))).doesNotThrowAnyException();
    assertThatCode(() -> adapter.lockForAddress(null)).doesNotThrowAnyException();
    assertThatCode(() -> adapter.lockForAddress("")).doesNotThrowAnyException();
  }

  @Test
  void isAnnotatedWithTestProfile() {
    Profile profile = NoopTreasuryAdvisoryLockAdapter.class.getAnnotation(Profile.class);
    assertThat(profile).isNotNull();
    assertThat(profile.value()).contains("test");
  }
}
