package momzzangseven.mztkbe.modules.auth.infrastructure.config;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
@RequiredArgsConstructor
public class WebClientConfig {
  private final AuthWebClientProperties authWebClientProperties;

  /** Configured WebClient used by Kakao/Google adapters to call external APIs. */
  @Bean
  public WebClient webClient() {
    HttpClient httpClient =
        HttpClient.create()
            .option(
                ChannelOption.CONNECT_TIMEOUT_MILLIS,
                authWebClientProperties.getConnectTimeoutMillis())
            .responseTimeout(
                Duration.ofSeconds(authWebClientProperties.getResponseTimeoutSeconds()));

    return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).build();
  }
}
