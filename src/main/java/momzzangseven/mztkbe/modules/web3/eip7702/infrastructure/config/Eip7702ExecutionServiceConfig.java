package momzzangseven.mztkbe.modules.web3.eip7702.infrastructure.config;

import momzzangseven.mztkbe.modules.web3.eip7702.application.port.in.ManageExecutionEip7702UseCase;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.in.PrepareTokenTransferExecutionSupportUseCase;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702AuthorizationPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702ChainPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.VerifyExecutionSignaturePort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.service.ManageExecutionEip7702Service;
import momzzangseven.mztkbe.modules.web3.eip7702.application.service.PrepareTokenTransferExecutionSupportService;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnUserExecutionEnabled;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnUserExecutionEnabled
public class Eip7702ExecutionServiceConfig {

  @Bean
  ManageExecutionEip7702UseCase manageExecutionEip7702UseCase(
      Eip7702AuthorizationPort eip7702AuthorizationPort,
      Eip7702ChainPort eip7702ChainPort,
      Eip7702TransactionCodecPort eip7702TransactionCodecPort,
      VerifyExecutionSignaturePort verifyExecutionSignaturePort) {
    return new ManageExecutionEip7702Service(
        eip7702AuthorizationPort,
        eip7702ChainPort,
        eip7702TransactionCodecPort,
        verifyExecutionSignaturePort);
  }

  @Bean
  PrepareTokenTransferExecutionSupportUseCase prepareTokenTransferExecutionSupportUseCase(
      Eip7702ChainPort eip7702ChainPort,
      Eip7702AuthorizationPort eip7702AuthorizationPort,
      Eip7702TransactionCodecPort eip7702TransactionCodecPort) {
    return new PrepareTokenTransferExecutionSupportService(
        eip7702ChainPort, eip7702AuthorizationPort, eip7702TransactionCodecPort);
  }
}
