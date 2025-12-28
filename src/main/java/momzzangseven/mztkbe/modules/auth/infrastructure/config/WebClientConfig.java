package momzzangseven.mztkbe.modules.auth.infrastructure.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    /**
     * Configured WebClient used by Kakao/Google adapters to call external APIs.
     */
  @Bean
  public WebClient webClient() {
      HttpClient httpClient =
              HttpClient.create()
                      .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3_000)
                      .responseTimeout(Duration.ofSeconds(5));

      return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).build();
  }
}
