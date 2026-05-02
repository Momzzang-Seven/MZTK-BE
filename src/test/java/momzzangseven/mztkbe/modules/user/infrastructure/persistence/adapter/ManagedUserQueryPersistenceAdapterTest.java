package momzzangseven.mztkbe.modules.user.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.List;
import momzzangseven.mztkbe.modules.user.application.dto.GetManagedUsersQuery;
import momzzangseven.mztkbe.modules.user.application.dto.ManagedUserView;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.entity.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@DisplayName("ManagedUserQueryPersistenceAdapter DataJpaTest")
class ManagedUserQueryPersistenceAdapterTest {

  @Autowired private TestEntityManager em;

  @Test
  @DisplayName("기본 조회는 ADMIN 계정을 제외한 사용자 프로필만 반환한다")
  void load_excludesAdmins() {
    ManagedUserQueryPersistenceAdapter adapter =
        new ManagedUserQueryPersistenceAdapter(new JPAQueryFactory(em.getEntityManager()));

    Long userOneId =
        persistUser(
            "user1@test.com", "user-one", UserRole.USER, Instant.parse("2025-01-10T10:00:00Z"));
    Long userTwoId =
        persistUser(
            "trainer1@test.com",
            "trainer-one",
            UserRole.TRAINER,
            Instant.parse("2025-01-09T10:00:00Z"));
    persistUser(
        "admin@test.com", "seed-admin", UserRole.ADMIN_SEED, Instant.parse("2025-01-11T10:00:00Z"));

    List<ManagedUserView> items = adapter.load(new GetManagedUsersQuery(null, null, null));

    assertThat(items).hasSize(2);
    assertThat(items)
        .extracting(ManagedUserView::userId)
        .containsExactlyInAnyOrder(userOneId, userTwoId);
    assertThat(items).extracting(ManagedUserView::role).doesNotContain(UserRole.ADMIN_SEED);
  }

  @Test
  @DisplayName("role, search, candidateUserIds 필터를 함께 적용할 수 있다")
  void load_appliesRoleSearchAndCandidateUserFilters() {
    ManagedUserQueryPersistenceAdapter adapter =
        new ManagedUserQueryPersistenceAdapter(new JPAQueryFactory(em.getEntityManager()));

    persistUser("user1@test.com", "alpha", UserRole.USER, Instant.parse("2025-01-10T10:00:00Z"));
    Long trainerId =
        persistUser(
            "trainer@test.com",
            "target-trainer",
            UserRole.TRAINER,
            Instant.parse("2025-01-09T10:00:00Z"));

    List<ManagedUserView> items =
        adapter.load(
            new GetManagedUsersQuery("trainer", UserRole.TRAINER, java.util.Set.of(trainerId)));

    assertThat(items).hasSize(1);
    assertThat(items.get(0).userId()).isEqualTo(trainerId);
    assertThat(items.get(0).role()).isEqualTo(UserRole.TRAINER);
  }

  private Long persistUser(String email, String nickname, UserRole role, Instant joinedAt) {
    UserEntity user = UserEntity.builder().email(email).nickname(nickname).role(role).build();
    em.persist(user);
    user.setCreatedAt(joinedAt);
    user.setUpdatedAt(joinedAt);
    em.flush();
    return user.getId();
  }
}
