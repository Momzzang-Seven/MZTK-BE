package momzzangseven.mztkbe.modules.admin.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.admin.infrastructure.persistence.entity.AdminAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for {@code admin_accounts}. */
public interface AdminAccountJpaRepository extends JpaRepository<AdminAccountEntity, Long> {

  Optional<AdminAccountEntity> findByUserIdAndDeletedAtIsNull(Long userId);

  Optional<AdminAccountEntity> findByLoginIdAndDeletedAtIsNull(String loginId);

  boolean existsByLoginIdAndDeletedAtIsNull(String loginId);

  long countByDeletedAtIsNull();

  List<AdminAccountEntity> findAllByDeletedAtIsNull();

  /** Bulk soft-delete all active admin accounts. Returns the number of affected rows. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("UPDATE AdminAccountEntity a SET a.deletedAt = :now WHERE a.deletedAt IS NULL")
  int softDeleteAll(@Param("now") Instant now);
}
