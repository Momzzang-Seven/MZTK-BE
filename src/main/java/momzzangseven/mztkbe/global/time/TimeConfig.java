package momzzangseven.mztkbe.global.time;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

  @Bean
  public ZoneId appZoneId(@Value("${spring.jackson.time-zone:Asia/Seoul}") String zoneId) {
    return ZoneId.of(zoneId);
  }

  @Bean
  public Clock appClock(ZoneId appZoneId) {
    return Clock.system(appZoneId);
  }
}
