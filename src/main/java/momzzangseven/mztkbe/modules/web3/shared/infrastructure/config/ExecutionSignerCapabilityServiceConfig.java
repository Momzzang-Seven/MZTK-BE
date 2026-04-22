package momzzangseven.mztkbe.modules.web3.shared.infrastructure.config;

import momzzangseven.mztkbe.modules.web3.shared.application.port.in.ProbeExecutionSignerCapabilityUseCase;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.LoadExecutionSignerConfigPort;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.ProbeExecutionSignerCapabilityPort;
import momzzangseven.mztkbe.modules.web3.shared.application.service.ProbeExecutionSignerCapabilityService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnInternalExecutionEnabled
@ConditionalOnBean({LoadExecutionSignerConfigPort.class, ProbeExecutionSignerCapabilityPort.class})
public class ExecutionSignerCapabilityServiceConfig {

  @Bean
  ProbeExecutionSignerCapabilityUseCase probeExecutionSignerCapabilityUseCase(
      LoadExecutionSignerConfigPort loadExecutionSignerConfigPort,
      ProbeExecutionSignerCapabilityPort probeExecutionSignerCapabilityPort) {
    return new ProbeExecutionSignerCapabilityService(
        loadExecutionSignerConfigPort, probeExecutionSignerCapabilityPort);
  }
}
