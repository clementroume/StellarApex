package com.antares.api.aop;

import com.antares.api.annotation.RateLimit;
import com.antares.api.exception.TooManyRequestsException;
import com.antares.api.service.RateLimitingService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * RateLimitingAspect is an aspect that intercepts methods annotated with @RateLimit to enforce rate
 * limiting based on IP address and method signature.
 */
@Aspect
@Component
public class RateLimitingAspect {

  private final RateLimitingService rateLimitingService;

  /**
   * Constructs a RateLimitingAspect with the specified RateLimitingService.
   *
   * @param rateLimitingService the service to handle rate limiting logic
   */
  public RateLimitingAspect(RateLimitingService rateLimitingService) {
    this.rateLimitingService = rateLimitingService;
  }

  /**
   * Intercepts methods annotated with @RateLimit and applies rate limiting logic.
   *
   * @param joinPoint the join point representing the method being intercepted
   * @param rateLimit the RateLimit annotation instance
   * @return the result of the method execution if allowed, otherwise throws
   *     TooManyRequestsException
   * @throws Throwable if the intercepted method throws an exception
   */
  @Around("@annotation(rateLimit)")
  public Object rateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {

    HttpServletRequest request =
        ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();

    String ipAddress = request.getHeader("X-Forwarded-For");
    if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
      ipAddress = request.getRemoteAddr();
    } else {
      ipAddress = ipAddress.split(",")[0].trim();
    }

    if (rateLimitingService.isAllowed(
        "rateLimit:" + joinPoint.getSignature().toShortString() + ":" + ipAddress,
        rateLimit.limit(),
        rateLimit.duration(),
        rateLimit.unit())) {
      return joinPoint.proceed();
    } else {
      throw new TooManyRequestsException("error.rate.limit.exceeded");
    }
  }
}
