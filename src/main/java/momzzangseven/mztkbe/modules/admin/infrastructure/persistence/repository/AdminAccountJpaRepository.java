package momzzangseven.mztkbe.modules.admin.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.admin.infrastructure.persistence.entity.AdminAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for {@code admin_accounts}. */
public interface AdminAccountJpaRepository extends JpaRepository<AdminAccountEntity, Long> {

  Optional<AdminAccountEntity> findByUserIdAndDeletedAtIsNull(Long userId);

  Optional<AdminAccountEntity> findByLoginIdAndDeletedAtIsNull(String loginId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT a FROM AdminAccountEntity a WHERE a.loginId = :loginId AND a.deletedAt IS NULL")
  Optional<AdminAccountEntity> findByLoginIdAndDeletedAtIsNullForUpdate(
      @Param("loginId") String loginId);

  boolean existsByLoginIdAndDeletedAtIsNull(String loginId);

  long countByDeletedAtIsNull();

  @Query(
      value =
          "SELECT COUNT(*) FROM admin_accounts a"
              + " JOIN users u ON a.user_id = u.id"
              + " WHERE a.deleted_at IS NULL AND u.role = :role",
      nativeQuery = true)
  long countActiveByRole(@Param("role") String role);

  List<AdminAccountEntity> findAllByDeletedAtIsNull();

  /** Hard-delete all admin accounts. Returns the number of affected rows. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM AdminAccountEntity a")
  int deleteAllInBulk();
}
