package momzzangseven.mztkbe.modules.level.infrastructure.repository;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.XpPolicyEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface XpPolicyJpaRepository extends JpaRepository<XpPolicyEntity, Long> {

  @Query(
      """
      select xp
      from XpPolicyEntity xp
      where xp.enabled = true
        and xp.effectiveFrom <= :at
        and (xp.effectiveTo is null or xp.effectiveTo > :at)
      order by xp.type asc, xp.effectiveFrom desc
      """)
  List<XpPolicyEntity> findActivePolicies(@Param("at") LocalDateTime at);

  @Query(
      """
      select xp
      from XpPolicyEntity xp
      where xp.type = :type
        and xp.enabled = true
        and xp.effectiveFrom <= :at
        and (xp.effectiveTo is null or xp.effectiveTo > :at)
      order by xp.effectiveFrom desc
      """)
  List<XpPolicyEntity> findActiveByType(
      @Param("type") XpType type, @Param("at") LocalDateTime at, Pageable pageable);
}
