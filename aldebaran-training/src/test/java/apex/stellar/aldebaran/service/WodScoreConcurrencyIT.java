package apex.stellar.aldebaran.service;

import static org.assertj.core.api.Assertions.assertThat;

import apex.stellar.aldebaran.config.BaseIntegrationTest;
import apex.stellar.aldebaran.dto.WodScoreRequest;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.entities.WodScore;
import apex.stellar.aldebaran.model.entities.WodScore.ScalingLevel;
import apex.stellar.aldebaran.repository.WodRepository;
import apex.stellar.aldebaran.repository.WodScoreRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.ConcurrencyFailureException;

@Slf4j
class WodScoreConcurrencyIT extends BaseIntegrationTest {

  @Autowired private WodScoreService wodScoreService;
  @Autowired private WodRepository wodRepository;
  @Autowired private WodScoreRepository scoreRepository;

  private Long wodId;
  private final Long userId = 999L; // Arbitrary User ID for the test

  @BeforeEach
  void setUpData() {
    // SQL-specific cleanup (BaseIntegrationTest only cleans Redis)
    scoreRepository.deleteAll();
    wodRepository.deleteAll();

    // Creating a "Time" WOD (lower is better)
    Wod wod = Wod.builder().title("Grace").wodType(WodType.GIRLS).scoreType(ScoreType.TIME).build();
    wodId = wodRepository.save(wod).getId();
  }

  @Test
  @DisplayName("Concurrency: Should guarantee exactly one PR despite parallel writes")
  void shouldGuaranteeUniquePrUnderConcurrency() {
    // GIVEN
    int threadCount = 10;

    AtomicInteger successfulWrites = new AtomicInteger(0);
    List<Future<?>> futures = new ArrayList<>();

    // WHEN
    try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
      CountDownLatch latch = new CountDownLatch(threadCount);

      for (int i = 0; i < threadCount; i++) {
        // Simulating decreasing scores: 100, 99, ... 91.
        // The last thread (91s) should theoretically hold the PR.
        final int timeSeconds = 100 - i;

        futures.add(
            executor.submit(
                () -> {
                  try {
                    // 1. Synchronization: All threads wait here to start together
                    latch.countDown();
                    latch.await();

                    // 2. DTO Construction
                    // Note: Explicitly passing userId to bypass empty SecurityContextHolder in
                    // threads
                    WodScoreRequest request =
                        new WodScoreRequest(
                            userId,
                            wodId,
                            LocalDate.now(),
                            0,
                            timeSeconds, // minutes, seconds
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            ScalingLevel.RX,
                            false,
                            null,
                            null);

                    // 3. Direct Service call (Transaction Boundary)
                    wodScoreService.logScore(request);
                    successfulWrites.incrementAndGet();

                  } catch (ConcurrencyFailureException e) {
                    // In SERIALIZABLE mode, DB might reject concurrent transactions.
                    // This is valid behavior: rejection is preferred over data corruption.
                    log.info(
                        "Transaction rejected by DB (Expected behavior in Serializable): {}",
                        e.getMessage());
                  } catch (Exception e) {
                    log.error("Unexpected error during concurrent execution", e);
                  }
                }));
      }

      // Waiting for threads to finish
      for (Future<?> future : futures) {
        try {
          future.get(5, TimeUnit.SECONDS); // Safety timeout
        } catch (Exception e) {
          log.error("Error waiting for future completion", e);
        }
      }
    }

    // THEN
    List<WodScore> allScores = scoreRepository.findByWodIdAndUserId(wodId, userId);

    // Check 1: Data exists (at least one transaction succeeded)
    assertThat(allScores).isNotEmpty();
    log.info("Scores persisted successfully: {}", allScores.size());

    // Check 2: CRITICAL - There must be exactly one score marked PR=true
    long prCount = allScores.stream().filter(WodScore::isPersonalRecord).count();
    assertThat(prCount)
        .as("There must be exactly ONE Personal Record marked, irrespective of concurrency")
        .isEqualTo(1);

    // Check 3: The marked PR must be mathematically the best among recorded ones
    WodScore markedPr =
        allScores.stream().filter(WodScore::isPersonalRecord).findFirst().orElseThrow();

    WodScore bestActualScore =
        allScores.stream().min(Comparator.comparingInt(WodScore::getTimeSeconds)).orElseThrow();

    assertThat(markedPr.getId())
        .as("The marked PR matches the actual best score in DB")
        .isEqualTo(bestActualScore.getId());
  }
}
