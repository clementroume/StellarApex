package apex.stellar.aldebaran.dto;

import java.util.List;

/**
 * Reference values of the session forms.
 *
 * @param types Session types.
 * @param disciplines Disciplines.
 * @param kinds Block kinds.
 * @param stimuli Block stimuli.
 * @param visibilityPolicies Visibility policies (a program only uses ADVANCE and DAY_OF).
 */
public record SessionReferenceData(
    List<String> types,
    List<String> disciplines,
    List<String> kinds,
    List<String> stimuli,
    List<String> visibilityPolicies) {}
