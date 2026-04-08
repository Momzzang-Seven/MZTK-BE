package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.GetTransferQuery;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.GetTransferUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.LoadTransferExecutionIntentPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class GetTransferService implements GetTransferUseCase {

  private final LoadTransferExecutionIntentPort loadTransferExecutionIntentPort;
  private final GetExecutionIntentUseCase getExecutionIntentUseCase;

  @Override
  public GetExecutionIntentResult execute(GetTransferQuery query) {
    String executionIntentId =
        loadTransferExecutionIntentPort
            .findLatestExecutionIntentId(query.requesterUserId(), query.resourceId())
            .orElseThrow(
                () ->
                    new Web3InvalidInputException(
                        "transfer resource not found: " + query.resourceId()));

    return getExecutionIntentUseCase.execute(
        new GetExecutionIntentQuery(query.requesterUserId(), executionIntentId));
  }
}
