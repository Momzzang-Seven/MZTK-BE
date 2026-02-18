package momzzangseven.mztkbe.modules.level.infrastructure.persistence.adapter;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.level.application.port.out.PolicyPort;
import momzzangseven.mztkbe.modules.level.domain.model.LevelPolicy;
import momzzangseven.mztkbe.modules.level.domain.model.XpPolicy;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.LevelPolicyEntity;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.XpPolicyEntity;
import momzzangseven.mztkbe.modules.level.infrastructure.repository.LevelPolicyJpaRepository;
import momzzangseven.mztkbe.modules.level.infrastructure.repository.XpPolicyJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PolicyPersistenceAdapter implements PolicyPort {

  private final LevelPolicyJpaRepository levelPolicyJpaRepository;
  private final XpPolicyJpaRepository xpPolicyJpaRepository;

  @Override
  @Transactional(readOnly = true)
  public Optional<LevelPolicy> loadLevelPolicy(int currentLevel, LocalDateTime at) {
    return levelPolicyJpaRepository
        .findActiveByLevel(currentLevel, at, PageRequest.of(0, 1))
        .stream()
        .findFirst()
        .map(LevelPolicyEntity::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<LevelPolicy> loadLevelPolicies(LocalDateTime at) {
    Map<Integer, LevelPolicyEntity> latestByLevel =
        levelPolicyJpaRepository.findActivePolicies(at).stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    LevelPolicyEntity::getLevel,
                    Function.identity(),
                    (a, b) -> a.getEffectiveFrom().isAfter(b.getEffectiveFrom()) ? a : b));

    return latestByLevel.values().stream()
        .sorted(Comparator.comparingInt(LevelPolicyEntity::getLevel))
        .map(LevelPolicyEntity::toDomain)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<XpPolicy> loadXpPolicy(XpType type, LocalDateTime at) {
    return xpPolicyJpaRepository.findActiveByType(type, at, PageRequest.of(0, 1)).stream()
        .findFirst()
        .map(XpPolicyEntity::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<XpPolicy> loadXpPolicies(LocalDateTime at) {
    Map<XpType, XpPolicyEntity> latestByType =
        xpPolicyJpaRepository.findActivePolicies(at).stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    XpPolicyEntity::getType,
                    Function.identity(),
                    (a, b) -> a.getEffectiveFrom().isAfter(b.getEffectiveFrom()) ? a : b));

    return latestByType.values().stream()
        .sorted(Comparator.comparing(XpPolicyEntity::getType))
        .map(XpPolicyEntity::toDomain)
        .toList();
  }
}
