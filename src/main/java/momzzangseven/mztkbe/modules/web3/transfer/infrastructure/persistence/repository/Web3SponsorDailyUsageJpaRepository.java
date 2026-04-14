package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity.Web3SponsorDailyUsageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface Web3SponsorDailyUsageJpaRepository
    extends JpaRepository<Web3SponsorDailyUsageEntity, Long> {

  Optional<Web3SponsorDailyUsageEntity> findByUserIdAndUsageDateKst(
      Long userId, LocalDate usageDateKst);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select u from Web3SponsorDailyUsageEntity u"
          + " where u.userId = :userId and u.usageDateKst = :usageDateKst")
  Optional<Web3SponsorDailyUsageEntity> findForUpdate(
      @Param("userId") Long userId, @Param("usageDateKst") LocalDate usageDateKst);

  @Query(
      "select u.id from Web3SponsorDailyUsageEntity u"
          + " where u.usageDateKst < :cutoffDate order by u.usageDateKst asc, u.id asc")
  List<Long> findUsageIdsForCleanup(@Param("cutoffDate") LocalDate cutoffDate, Pageable pageable);

  long deleteByIdIn(List<Long> ids);
}
