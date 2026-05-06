package momzzangseven.mztkbe.modules.web3.shared.infrastructure.config;

import momzzangseven.mztkbe.modules.web3.shared.application.port.in.ProbeExecutionSignerCapabilityUseCase;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.ProbeExecutionSignerCapabilityPort;
import momzzangseven.mztkbe.modules.web3.shared.application.service.ProbeExecutionSignerCapabilityService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnInternalExecutionEnabled
@ConditionalOnBean(ProbeExecutionSignerCapabilityPort.class)
public class ExecutionSignerCapabilityServiceConfig {

  @Bean
  ProbeExecutionSignerCapabilityUseCase probeExecutionSignerCapabilityUseCase(
      ProbeExecutionSignerCapabilityPort probeExecutionSignerCapabilityPort) {
    return new ProbeExecutionSignerCapabilityService(probeExecutionSignerCapabilityPort);
  }
}
