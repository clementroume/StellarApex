package apex.stellar.aldebaran.config;

import apex.stellar.aldebaran.service.MovementRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Rebuilds {@code movement_records} from the existing scores at startup when the table is empty
 * (first start after former V18, restored database; D62). Idempotent: nothing happens once records exist,
 * and the scores written afterwards keep them up to date.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class MovementRecordBackfill implements ApplicationRunner {

  private final MovementRecordService movementRecordService;

  @Override
  public void run(ApplicationArguments args) {
    int rebuilt = movementRecordService.rebuildAllIfEmpty();
    if (rebuilt > 0) {
      log.info("Movement records rebuilt for {} athlete / movement pairs", rebuilt);
    }
  }
}
