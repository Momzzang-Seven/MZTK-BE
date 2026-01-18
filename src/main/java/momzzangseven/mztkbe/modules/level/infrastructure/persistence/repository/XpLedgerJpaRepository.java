package momzzangseven.mztkbe.modules.level.infrastructure.persistence.repository;

import java.time.LocalDate;
import momzzangseven.mztkbe.modules.level.domain.model.XpType;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.XpLedgerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface XpLedgerJpaRepository extends JpaRepository<XpLedgerEntity, Long> {

  boolean existsByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

  int countByUserIdAndTypeAndEarnedOn(Long userId, XpType type, LocalDate earnedOn);

  boolean existsByUserIdAndTypeAndEarnedOnBetween(
      Long userId, XpType type, LocalDate startDate, LocalDate endDate);

  @Query(
      """
      select count(distinct xl.earnedOn)
      from XpLedgerEntity xl
      where xl.userId = :userId
        and xl.type = :type
        and xl.earnedOn between :startDate and :endDate
      """)
  long countDistinctEarnedOn(
      @Param("userId") Long userId,
      @Param("type") XpType type,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);
}
