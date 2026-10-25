package apex.stellar.aldebaran.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Audits the writes made through an admin support session.
 *
 * <p>When Antares sends {@code X-Auth-Actor-Id} (mapped onto {@link
 * AldebaranUserPrincipal#getActorId()}), a platform admin is acting as another user. Every request
 * other than GET/HEAD/OPTIONS is then logged at INFO with the actor, the target user, the method,
 * the path and the response status. The request itself is not changed: rights stay those of the
 * target user.
 */
@Slf4j
public class SupportSessionAuditFilter extends OncePerRequestFilter {

  private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain chain)
      throws ServletException, IOException {

    AldebaranUserPrincipal principal = currentPrincipal();
    if (principal == null
        || principal.getActorId() == null
        || SAFE_METHODS.contains(request.getMethod())) {
      chain.doFilter(request, response);
      return;
    }

    try {
      chain.doFilter(request, response);
    } finally {
      log.info(
          "Support session write: actorId={}, userId={}, method={}, path={}, status={}",
          principal.getActorId(),
          principal.getId(),
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus());
    }
  }

  private static AldebaranUserPrincipal currentPrincipal() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.getPrincipal() instanceof AldebaranUserPrincipal principal
        ? principal
        : null;
  }
}
