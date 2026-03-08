package momzzangseven.mztkbe.modules.level.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.level.application.dto.GetLevelPoliciesResult;
import momzzangseven.mztkbe.modules.level.application.port.out.PolicyPort;
import momzzangseven.mztkbe.modules.level.domain.model.LevelPolicy;
import momzzangseven.mztkbe.modules.level.domain.model.XpPolicy;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetLevelPoliciesServiceTest {

  @Mock private PolicyPort policyPort;

  @InjectMocks private GetLevelPoliciesService service;

  @Test
  void execute_shouldMapDomainPoliciesToItems() {
    when(policyPort.loadLevelPolicies(any(LocalDateTime.class)))
        .thenReturn(
            List.of(
                LevelPolicy.builder()
                    .id(1L)
                    .level(3)
                    .requiredXp(200)
                    .rewardMztk(9)
                    .enabled(true)
                    .build()));
    when(policyPort.loadXpPolicies(any(LocalDateTime.class)))
        .thenReturn(
            List.of(
                XpPolicy.builder()
                    .id(2L)
                    .type(XpType.CHECK_IN)
                    .xpAmount(10)
                    .dailyCap(1)
                    .enabled(true)
                    .build()));

    GetLevelPoliciesResult result = service.execute();

    assertThat(result.levelPolicies()).hasSize(1);
    assertThat(result.levelPolicies().getFirst().currentLevel()).isEqualTo(3);
    assertThat(result.levelPolicies().getFirst().toLevel()).isEqualTo(4);
    assertThat(result.xpPolicies()).hasSize(1);
    assertThat(result.xpPolicies().getFirst().type()).isEqualTo(XpType.CHECK_IN);
  }

  @Test
  void execute_shouldReturnEmptyListsWhenNoPolicy() {
    when(policyPort.loadLevelPolicies(any(LocalDateTime.class))).thenReturn(List.of());
    when(policyPort.loadXpPolicies(any(LocalDateTime.class))).thenReturn(List.of());

    GetLevelPoliciesResult result = service.execute();

    assertThat(result.levelPolicies()).isEmpty();
    assertThat(result.xpPolicies()).isEmpty();
  }
}
