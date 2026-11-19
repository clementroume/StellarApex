package apex.stellar.aldebaran.demo;

import apex.stellar.aldebaran.dto.SessionBlockRequest;
import apex.stellar.aldebaran.dto.SessionRequest;
import apex.stellar.aldebaran.dto.WodCopyRequest;
import apex.stellar.aldebaran.dto.WodRequest;
import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.Session.Discipline;
import apex.stellar.aldebaran.model.entities.Session.SessionType;
import apex.stellar.aldebaran.model.entities.Session.VisibilityPolicy;
import apex.stellar.aldebaran.model.entities.SessionBlock.Kind;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.SessionRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import apex.stellar.aldebaran.service.SessionService;
import apex.stellar.aldebaran.service.WodService;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The demo programs (resource {@code demo/programs.json}, written from the reviewed design in
 * {@code docs-private/demo}): a balanced 12-week programming for "Spartacus CrossFit" and an
 * unbalanced one, 6 days a week, for "Zeus Programming", centered on the seed day (6 weeks before,
 * the seed week, 5 after). Written through the services as the gym's holder ({@link DemoActor}), so
 * every rule of the app applies: catalog checks, official Scaled versions, team and competition
 * WODs, visibility.
 *
 * <p>Idempotent per program: nothing is done when the gym already has a session whose title starts
 * with the program's prefix. A session the services refuse is logged and skipped.
 */
@Slf4j
@Component
@Profile("demo")
class DemoPrograms {

  static final String RESOURCE = "demo/programs.json";

  private final ObjectMapper objectMapper;
  private final SessionRepository sessionRepository;
  private final MovementRepository movementRepository;
  private final WodRepository wodRepository;
  private final WodService wodService;
  private final SessionService sessionService;
  private final DemoActor actor;

  DemoPrograms(
      ObjectMapper objectMapper,
      SessionRepository sessionRepository,
      MovementRepository movementRepository,
      WodRepository wodRepository,
      WodService wodService,
      SessionService sessionService,
      DemoActor actor) {
    this.objectMapper = objectMapper;
    this.sessionRepository = sessionRepository;
    this.movementRepository = movementRepository;
    this.wodRepository = wodRepository;
    this.wodService = wodService;
    this.sessionService = sessionService;
    this.actor = actor;
  }

  /** A program of the resource. */
  record Program(String key, String gym, String author, String titlePrefix, int firstWeek, JsonNode node) {}

  /**
   * Reads the programs of the resource.
   *
   * @return The programs, Sparta first.
   */
  List<Program> programs() {
    try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
      JsonNode root = objectMapper.readTree(in);
      List<Program> out = new ArrayList<>();
      for (JsonNode p : root.get("programs")) {
        out.add(
            new Program(
                p.get("key").asString(),
                p.get("gym").asString(),
                p.get("author").asString(),
                p.get("titlePrefix").asString(),
                p.get("firstWeek").asInt(),
                p));
      }
      return out;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Monday of the first week of a program: the seed week moved by its first week.
   *
   * @param program The program.
   * @param today The seed day.
   * @return The Monday.
   */
  static LocalDate firstMonday(Program program, LocalDate today) {
    return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(program.firstWeek());
  }

  /**
   * The day of a session of the program.
   *
   * @param program The program.
   * @param session The session node.
   * @param today The seed day.
   * @return Its date.
   */
  static LocalDate dateOf(Program program, JsonNode session, LocalDate today) {
    return firstMonday(program, today)
        .plusWeeks(session.get("week").asInt() - 1L)
        .with(TemporalAdjusters.nextOrSame(DayOfWeek.valueOf(session.get("day").asString())));
  }

  /**
   * Whether the program is already in its gym.
   *
   * @param program The program.
   * @param gymId Its gym.
   * @return True when a session with its prefix exists.
   */
  boolean seeded(Program program, Long gymId) {
    return sessionRepository.existsByGymIdAndTitleStartingWith(gymId, program.titlePrefix());
  }

  /**
   * Writes a program: the box copies of its benchmarks, then its sessions.
   *
   * @param program The program.
   * @param gymId Its gym.
   * @param authorId Its holder (Owner or Programmer).
   * @param today The seed day.
   * @return Sessions created.
   */
  int seed(Program program, Long gymId, Long authorId, LocalDate today) {
    Map<String, Long> movementIds = new HashMap<>();
    for (Movement m : movementRepository.findAll()) {
      movementIds.put(m.getName(), m.getId());
    }
    Map<String, Long> publicIds = new HashMap<>();
    for (Wod w : wodRepository.findAll()) {
      if (w.isPublic() && w.getGymId() == null) {
        publicIds.putIfAbsent(w.getTitle(), w.getId());
      }
    }
    Map<String, Long> copies = new HashMap<>();
    for (JsonNode copy : program.node().get("copies")) {
      Long id = copy(copy, gymId, authorId, movementIds, publicIds);
      if (id != null) {
        copies.put(copy.get("wod").get("title").asString(), id);
      }
    }
    VisibilityPolicy policy = VisibilityPolicy.valueOf(program.node().get("visibilityPolicy").asString());
    int created = 0;
    for (JsonNode session : program.node().get("sessions")) {
      String title = session.get("title").asString();
      try {
        SessionRequest request =
            new SessionRequest(
                SessionType.WOD,
                Discipline.CROSSFIT,
                dateOf(program, session, today),
                title,
                null,
                null,
                gymId,
                policy,
                null,
                blocks(session, gymId, movementIds, publicIds, copies));
        actor.run(authorId, () -> sessionService.createSession(request, actor.principal(authorId)));
        created++;
      } catch (RuntimeException e) {
        log.warn("Demo program {}: session '{}' skipped ({})", program.key(), title, e.getMessage());
      }
    }
    log.info("Demo program {}: {} sessions, {} benchmark copies", program.key(), created, copies.size());
    return created;
  }

  /** A box copy of a public benchmark with its official Scaled version, or a box WOD when the catalog lacks it. */
  private @Nullable Long copy(
      JsonNode copy, Long gymId, Long authorId, Map<String, Long> movementIds, Map<String, Long> publicIds) {
    String source = copy.get("source").asString();
    try {
      WodRequest request = wodRequest(copy.get("wod"), gymId, false, movementIds);
      Long publicId = publicIds.get(source);
      return actor.as(
          authorId,
          () -> {
            if (publicId == null) {
              return wodService.createWod(request).id();
            }
            // The copy keeps its link to the benchmark; the box then writes its own version
            Long id = wodService.copyWod(publicId, new WodCopyRequest(gymId, false)).id();
            return wodService.updateWod(id, request).id();
          });
    } catch (RuntimeException e) {
      log.warn("Demo program: copy of '{}' skipped ({})", source, e.getMessage());
      return null;
    }
  }

  private List<SessionBlockRequest> blocks(
      JsonNode session,
      Long gymId,
      Map<String, Long> movementIds,
      Map<String, Long> publicIds,
      Map<String, Long> copies) {
    List<SessionBlockRequest> blocks = new ArrayList<>();
    for (JsonNode b : session.get("blocks")) {
      boolean competition = b.path("competition").asBoolean(false);
      Long wodId = null;
      WodRequest wod = null;
      if (b.has("copy")) {
        wodId = copies.get(b.get("copy").asString());
        if (wodId == null) {
          throw new IllegalStateException("no box copy of " + b.get("copy").asString());
        }
      } else if (b.has("public")) {
        // A public WOD borrowed as is (a test, a Zeus benchmark), else its own copy in the block
        wodId = publicIds.get(b.get("public").asString());
        if (wodId == null) {
          wod = wodRequest(b.get("fallback"), gymId, competition, movementIds);
        }
      } else if (b.has("wod")) {
        wod = wodRequest(b.get("wod"), gymId, competition, movementIds);
      }
      blocks.add(
          new SessionBlockRequest(
              null,
              Kind.valueOf(b.get("kind").asString()),
              text(b, "label"),
              text(b, "themeEn"),
              text(b, "themeFr"),
              b.path("optional").asBoolean(false),
              null,
              b.has("plannedMinutes") ? b.get("plannedMinutes").asInt() : null,
              text(b, "textEn"),
              text(b, "textFr"),
              competition,
              wodId,
              wod));
    }
    return blocks;
  }

  private static @Nullable String text(JsonNode node, String field) {
    JsonNode v = node.get(field);
    return v == null || v.isNull() ? null : v.asString();
  }

  /** A WOD of the resource as a request: catalog names become ids, the gym and flags are set. */
  WodRequest wodRequest(
      JsonNode spec, Long gymId, boolean competition, Map<String, Long> movementIds) {
    ObjectNode node = (ObjectNode) spec.deepCopy();
    node.put("gymId", gymId);
    node.put("isPublic", false);
    if (!node.has("competition")) {
      node.put("competition", competition);
    }
    for (JsonNode line : node.get("movements")) {
      resolve((ObjectNode) line, movementIds);
      if (line.has("scaled")) {
        resolve((ObjectNode) line.get("scaled"), movementIds);
      }
    }
    return objectMapper.treeToValue(node, WodRequest.class);
  }

  private static void resolve(ObjectNode line, Map<String, Long> movementIds) {
    String name = line.remove("movement").asString();
    Long id = movementIds.get(name);
    if (id == null) {
      throw new IllegalStateException("movement not in the catalog: " + name);
    }
    line.put("movementId", id);
  }
}
