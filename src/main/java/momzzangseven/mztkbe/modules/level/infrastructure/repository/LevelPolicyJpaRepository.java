package momzzangseven.mztkbe.modules.level.infrastructure.repository;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.LevelPolicyEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LevelPolicyJpaRepository extends JpaRepository<LevelPolicyEntity, Long> {

  @Query(
      """
      select lp
      from LevelPolicyEntity lp
      where lp.level = :level
        and lp.enabled = true
        and lp.effectiveFrom <= :at
        and (lp.effectiveTo is null or lp.effectiveTo > :at)
      order by lp.effectiveFrom desc
      """)
  List<LevelPolicyEntity> findActiveByLevel(
      @Param("level") int level, @Param("at") LocalDateTime at, Pageable pageable);

  @Query(
      """
      select lp
      from LevelPolicyEntity lp
      where lp.enabled = true
        and lp.effectiveFrom <= :at
        and (lp.effectiveTo is null or lp.effectiveTo > :at)
      order by lp.level asc, lp.effectiveFrom desc
      """)
  List<LevelPolicyEntity> findActivePolicies(@Param("at") LocalDateTime at);
}
