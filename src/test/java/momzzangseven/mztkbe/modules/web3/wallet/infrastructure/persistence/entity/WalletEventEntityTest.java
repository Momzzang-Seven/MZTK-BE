package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletEventType;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus;
import org.junit.jupiter.api.Test;

class WalletEventEntityTest {

  @Test
  void builder_storesFields() {
    WalletEventEntity entity =
        WalletEventEntity.builder()
            .id(1L)
            .walletAddress("0x" + "a".repeat(40))
            .eventType(WalletEventType.UNLINKED)
            .userId(7L)
            .previousUserId(7L)
            .previousStatus(WalletStatus.ACTIVE)
            .newStatus(WalletStatus.UNLINKED)
            .metadata("{\"reason\":\"user_request\"}")
            .occurredAt(Instant.parse("2026-03-01T00:00:00Z"))
            .build();

    assertThat(entity.getEventType()).isEqualTo(WalletEventType.UNLINKED);
    assertThat(entity.getPreviousStatus()).isEqualTo(WalletStatus.ACTIVE);
    assertThat(entity.getMetadata()).contains("user_request");
  }
}
