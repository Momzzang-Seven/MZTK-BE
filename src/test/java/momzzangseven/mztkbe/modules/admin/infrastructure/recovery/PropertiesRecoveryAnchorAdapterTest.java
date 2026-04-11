package momzzangseven.mztkbe.modules.admin.infrastructure.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.admin.infrastructure.config.RecoveryAnchorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PropertiesRecoveryAnchorAdapter 단위 테스트")
class PropertiesRecoveryAnchorAdapterTest {

  @Mock private RecoveryAnchorProperties properties;

  private PropertiesRecoveryAnchorAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new PropertiesRecoveryAnchorAdapter(properties);
  }

  @Test
  @DisplayName("[M-103] loadAnchor returns the value from RecoveryAnchorProperties")
  void loadAnchor_returnsValueFromProperties() {
    // given
    given(properties.getAnchor()).willReturn("test-recovery-anchor");

    // when
    String result = adapter.loadAnchor();

    // then
    assertThat(result).isEqualTo("test-recovery-anchor");
    verify(properties, times(1)).getAnchor();
  }

  @Test
  @DisplayName("[M-104] loadAnchor returns null when properties anchor is null")
  void loadAnchor_propertiesNull_returnsNull() {
    // given
    given(properties.getAnchor()).willReturn(null);

    // when
    String result = adapter.loadAnchor();

    // then
    assertThat(result).isNull();
  }

  @Test
  @DisplayName("[M-105] loadAnchor returns empty string when properties anchor is empty")
  void loadAnchor_propertiesEmpty_returnsEmptyString() {
    // given
    given(properties.getAnchor()).willReturn("");

    // when
    String result = adapter.loadAnchor();

    // then
    assertThat(result).isEmpty();
  }
}
