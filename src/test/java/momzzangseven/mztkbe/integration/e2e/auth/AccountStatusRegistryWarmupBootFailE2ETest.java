package momzzangseven.mztkbe.integration.e2e.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.MztkBeApplication;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadNonActiveUserStatusesPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * [MOM-464 / 리뷰 N1] {@code account.status-registry.enabled=true} 인데 denylist warm-up 이 끝내 성공하지 못하면
 * 애플리케이션 <b>부팅이 거부됨</b>을 실제 Spring 부팅 라이프사이클로 검증한다 (fail-closed).
 *
 * <p>리뷰 지적(N1): warm-up 이 실패해도 부팅이 진행되면, 비어 있는 denylist 를 hot path 가 읽어 {@code absence=ACTIVE} 로 모든
 * 사용자(비-ACTIVE 포함)를 인증 통과시키는 fail-open 이 발생한다. 대응: {@code AccountStatusRegistryWarmupRunner} 가
 * reconcile 을 최대 3회(1.5s backoff) 시도하고, {@code isReady()} 가 끝내 false 면 {@code
 * IllegalStateException} 으로 부팅을 거부한다.
 *
 * <p>이 계약은 unit({@code AccountStatusRegistryWarmupRunnerTest})으로도 검증되지만, 그것은 runner 를 직접 호출할 뿐 부팅
 * 라이프사이클(ApplicationRunner 가 컨텍스트 refresh 이후 실행되어 예외가 부팅을 중단시키는지)은 검증하지 못한다. 본 테스트는 {@link
 * SpringApplicationBuilder} 로 컨텍스트를 실제 기동해, reconcile 의 DB 읽기({@link
 * LoadNonActiveUserStatusesPort#loadAllNonActive()})가 항상 실패하도록 만든 뒤 {@code
 * SpringApplication.run(...)} 자체가 예외로 끝남(=부팅 거부)을 단언한다.
 *
 * <p>구현 노트:
 *
 * <ul>
 *   <li>실패 주입은 {@link FailingWarmupConfig} 의 {@code @Primary} {@link LoadNonActiveUserStatusesPort}
 *       (항상 throw)로 한다. {@code reconcile()} 은 fail-soft 라 이 예외를 삼키지만 {@code ready} 는 false 로 남고, 3회
 *       시도 후 runner 가 부팅을 거부한다.
 *   <li>{@link TestConfiguration} 이므로 컴포넌트 스캔에서 제외되어 다른 {@code @SpringBootTest} 컨텍스트에는 영향이 없다 — 오직
 *       이 테스트가 {@code SpringApplicationBuilder} 에 명시적으로 넘길 때만 등록된다.
 *   <li>{@code reconcile.cron} 은 사실상 발사되지 않는 값으로, 인접 스케줄러(web3/withdrawal)는 비활성으로 두어 부팅 중 잡음을 없앤다.
 *   <li>{@code @SpringBootTest} 가 아니라 직접 기동하므로 {@code E2ETestBase} 를 상속하지 않는다. integration 프로파일이
 *       라이브 PostgreSQL 을 요구하므로 {@code @Tag("e2e")} 로 둔다(스키마는 Flyway validate 로 읽기만 한다).
 * </ul>
 */
@Tag("e2e")
@DisplayName("[E2E] 계정상태 denylist warm-up 실패 시 부팅 거부 (MOM-464 N1 fail-closed)")
class AccountStatusRegistryWarmupBootFailE2ETest {

  @Test
  @DisplayName("enabled=true 인데 warm-up 이 denylist 를 적재하지 못하면 SpringApplication 부팅이 거부된다")
  void bootIsRefused_whenDenylistNeverWarmsUp() {
    assertThatThrownBy(
            () ->
                new SpringApplicationBuilder(MztkBeApplication.class, FailingWarmupConfig.class)
                    .web(WebApplicationType.NONE)
                    .profiles("integration")
                    .properties(
                        "account.status-registry.enabled=true",
                        // 측정 윈도우 안에서 reconcile 스케줄러가 발사되지 않도록 (2월 29일) 민다.
                        "account.status-registry.reconcile.cron=0 0 0 29 2 ?",
                        "account.status-registry.reconcile.zone=Asia/Seoul",
                        "mztk.admin.bootstrap.enabled=false",
                        "web3.reward-token.enabled=false",
                        "withdrawal.external-disconnect.fixed-delay=86400000")
                    .run())
        .as("warm-up 가 denylist 를 ready 로 만들지 못하면 부팅이 fail-closed 예외로 거부되어야 함")
        .satisfies(
            thrown ->
                assertThat(hasBootFailCause(thrown))
                    .as(
                        "부팅 거부 원인 체인에 warm-up 의 fail-closed IllegalStateException 이 있어야 함. 실제=%s",
                        thrown)
                    .isTrue());
  }

  /**
   * 부팅 실패 예외 체인에 warm-up runner 의 fail-closed {@link IllegalStateException} (메시지에 {@code "failed to
   * load after"} 포함)이 있는지 확인한다. {@code SpringApplication.run} 은 runner 예외를 직접 또는 래핑해 전파하므로 cause
   * 체인을 따라 내려가며 찾는다.
   */
  private static boolean hasBootFailCause(Throwable thrown) {
    for (Throwable t = thrown; t != null; t = t.getCause()) {
      String message = t.getMessage();
      if (t instanceof IllegalStateException
          && message != null
          && message.contains("failed to load after")) {
        return true;
      }
      if (t.getCause() == t) {
        break;
      }
    }
    return false;
  }

  /**
   * reconcile 의 DB 스냅샷 읽기를 항상 실패시키는 테스트 전용 설정. {@code @Primary} 라 실제 어댑터 대신 주입된다.
   * {@code @TestConfiguration} 이라 컴포넌트 스캔에서 제외되어 다른 컨텍스트에는 영향이 없다.
   */
  @TestConfiguration
  static class FailingWarmupConfig {

    @Bean
    @Primary
    LoadNonActiveUserStatusesPort failingLoadNonActiveUserStatusesPort() {
      return () -> {
        throw new IllegalStateException(
            "simulated DB outage: loadAllNonActive failed during warm-up");
      };
    }
  }
}
