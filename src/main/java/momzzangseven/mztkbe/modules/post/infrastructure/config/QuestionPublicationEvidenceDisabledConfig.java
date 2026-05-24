package momzzangseven.mztkbe.modules.post.infrastructure.config;

import momzzangseven.mztkbe.modules.post.application.port.out.LoadQuestionPublicationEvidencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionPublicationEvidence;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
    prefix = "web3.eip7702",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true)
public class QuestionPublicationEvidenceDisabledConfig {

  @Bean
  @ConditionalOnMissingBean(LoadQuestionPublicationEvidencePort.class)
  public LoadQuestionPublicationEvidencePort loadQuestionPublicationEvidencePort() {
    return (postId, requesterUserId) -> QuestionPublicationEvidence.unmanaged();
  }
}
