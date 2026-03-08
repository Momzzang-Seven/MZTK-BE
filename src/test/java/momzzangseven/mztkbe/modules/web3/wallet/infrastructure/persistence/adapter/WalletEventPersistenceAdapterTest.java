package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletEvent;
import momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.entity.WalletEventEntity;
import momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.repository.WalletEventJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletEventPersistenceAdapterTest {

  @Mock private WalletEventJpaRepository repository;
  @Mock private ObjectMapper objectMapper;

  private WalletEventPersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new WalletEventPersistenceAdapter(repository, objectMapper);
  }

  @Test
  void record_mapsEventAndSavesEntity() throws Exception {
    WalletEvent event =
        WalletEvent.registered(
            "0x" + "a".repeat(40), 7L, Map.of("ip", "127.0.0.1", "source", "api"));
    when(objectMapper.writeValueAsString(any())).thenReturn("{\"ip\":\"127.0.0.1\"}");

    adapter.record(event);

    ArgumentCaptor<WalletEventEntity> captor = ArgumentCaptor.forClass(WalletEventEntity.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getUserId()).isEqualTo(7L);
    assertThat(captor.getValue().getMetadata()).contains("127.0.0.1");
  }

  @Test
  void recordBatch_setsNullMetadata_whenJsonSerializationFails() throws Exception {
    WalletEvent event = WalletEvent.unlinked("0x" + "a".repeat(40), 7L, Map.of("reason", "manual"));
    when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});

    adapter.recordBatch(List.of(event));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<WalletEventEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(repository).saveAll(captor.capture());
    assertThat(captor.getValue()).hasSize(1);
    assertThat(captor.getValue().getFirst().getMetadata()).isNull();
  }
}
