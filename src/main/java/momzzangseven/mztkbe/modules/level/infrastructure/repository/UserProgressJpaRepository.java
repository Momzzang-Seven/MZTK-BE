package momzzangseven.mztkbe.modules.level.infrastructure.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.UserProgressEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProgressJpaRepository extends JpaRepository<UserProgressEntity, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select up from UserProgressEntity up where up.userId = :userId")
  Optional<UserProgressEntity> findByUserIdForUpdate(@Param("userId") Long userId);
}
