package com.antares.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * RateLimit annotation to specify rate limiting on API endpoints.
 *
 * <p>This annotation can be applied to controller methods to enforce a limit on the number of
 * requests allowed within a specified time duration.
 *
 * <p>Example usage:
 *
 * <pre>
 * &#64;RateLimit(limit = 10, duration = 1, unit = TimeUnit.MINUTES)
 * public ResponseEntity&lt;String&gt; myEndpoint() {
 *     // endpoint logic
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

  /** Maximum number of requests allowed within the specified duration. */
  int limit() default 100;

  /** Duration for which the rate limit is applied. */
  long duration() default 1;

  /** Time unit for the duration (e.g., SECONDS, MINUTES, HOURS). */
  TimeUnit unit() default TimeUnit.MINUTES;
}
