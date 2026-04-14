package momzzangseven.mztkbe.modules.admin.infrastructure.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import momzzangseven.mztkbe.modules.admin.domain.vo.GeneratedAdminCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LogBootstrapDeliveryAdapter 단위 테스트")
class LogBootstrapDeliveryAdapterTest {

  private LogBootstrapDeliveryAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new LogBootstrapDeliveryAdapter();
  }

  // ---------------------------------------------------------------------------
  // deliver() and getLastDelivery()
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("deliver() 및 getLastDelivery() 메서드")
  class DeliverAndGetLastDelivery {

    @Test
    @DisplayName("[M-97] deliver stores credentials retrievable via getLastDelivery")
    void deliver_storesCredentialsRetrievableViaGetLastDelivery() {
      // given
      List<GeneratedAdminCredentials> creds =
          List.of(
              new GeneratedAdminCredentials("12345678", "pass1"),
              new GeneratedAdminCredentials("87654321", "pass2"));

      // when
      adapter.deliver(creds);

      // then
      List<GeneratedAdminCredentials> lastDelivery = adapter.getLastDelivery();
      assertThat(lastDelivery).hasSize(2);
      assertThat(lastDelivery.get(0).loginId()).isEqualTo("12345678");
      assertThat(lastDelivery.get(0).plaintext()).isEqualTo("pass1");
      assertThat(lastDelivery.get(1).loginId()).isEqualTo("87654321");
      assertThat(lastDelivery.get(1).plaintext()).isEqualTo("pass2");
    }

    @Test
    @DisplayName("[M-98] getLastDelivery returns empty list before any deliver call")
    void getLastDelivery_beforeDeliverCall_returnsEmptyList() {
      // when
      List<GeneratedAdminCredentials> result = adapter.getLastDelivery();

      // then
      assertThat(result).isNotNull();
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("[M-99] deliver with empty list results in empty getLastDelivery")
    void deliver_emptyList_resultsInEmptyGetLastDelivery() {
      // when
      adapter.deliver(Collections.emptyList());

      // then
      assertThat(adapter.getLastDelivery()).isEmpty();
    }

    @Test
    @DisplayName("[M-100] deliver overwrites previous delivery")
    void deliver_overwritesPreviousDelivery() {
      // given
      adapter.deliver(List.of(new GeneratedAdminCredentials("11111111", "first")));

      // when
      adapter.deliver(List.of(new GeneratedAdminCredentials("22222222", "second")));

      // then
      List<GeneratedAdminCredentials> lastDelivery = adapter.getLastDelivery();
      assertThat(lastDelivery).hasSize(1);
      assertThat(lastDelivery.get(0).loginId()).isEqualTo("22222222");
      assertThat(lastDelivery.get(0).plaintext()).isEqualTo("second");
    }

    @Test
    @DisplayName("[M-101] getLastDelivery returns unmodifiable list")
    void getLastDelivery_returnsUnmodifiableList() {
      // given
      adapter.deliver(List.of(new GeneratedAdminCredentials("11111111", "pass")));

      // when
      List<GeneratedAdminCredentials> result = adapter.getLastDelivery();

      // then
      assertThatThrownBy(() -> result.add(new GeneratedAdminCredentials("99999999", "hack")))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("[M-102] deliver makes a defensive copy of the input list")
    void deliver_makesDefensiveCopyOfInputList() {
      // given
      List<GeneratedAdminCredentials> mutable =
          new ArrayList<>(List.of(new GeneratedAdminCredentials("11111111", "pass")));

      // when
      adapter.deliver(mutable);
      mutable.clear();

      // then
      assertThat(adapter.getLastDelivery()).hasSize(1);
    }
  }
}
