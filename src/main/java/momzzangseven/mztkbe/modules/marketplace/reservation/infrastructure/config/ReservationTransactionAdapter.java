package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.config;

import java.util.function.Supplier;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RunReservationTransactionPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class ReservationTransactionAdapter implements RunReservationTransactionPort {

  private final TransactionTemplate requiresNewTemplate;
  private final TransactionTemplate notSupportedTemplate;

  public ReservationTransactionAdapter(PlatformTransactionManager transactionManager) {
    this.requiresNewTemplate = new TransactionTemplate(transactionManager);
    this.requiresNewTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.notSupportedTemplate = new TransactionTemplate(transactionManager);
    this.notSupportedTemplate.setPropagationBehavior(
        TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
  }

  @Override
  public <T> T requiresNew(Supplier<T> supplier) {
    return requiresNewTemplate.execute(status -> supplier.get());
  }

  @Override
  public <T> T notSupported(Supplier<T> supplier) {
    return notSupportedTemplate.execute(status -> supplier.get());
  }
}
