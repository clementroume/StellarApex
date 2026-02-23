package apex.stellar.antares.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Spring component responsible for integrating OpenTelemetry with Logback.
 *
 * <p>This class ensures that the {@link OpenTelemetryAppender} is initialized with the provided
 * {@link OpenTelemetry} instance once the Spring application context is ready.
 */
@Component
class InstallOpenTelemetryAppender implements InitializingBean {

  private final OpenTelemetry openTelemetry;

  /**
   * Constructs a new instance with the required OpenTelemetry SDK.
   *
   * @param openTelemetry the OpenTelemetry instance to be used for log instrumentation
   */
  InstallOpenTelemetryAppender(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /** Installs the OpenTelemetry appender into the Logback configuration. */
  @Override
  public void afterPropertiesSet() {
    OpenTelemetryAppender.install(this.openTelemetry);
  }
}
