package apex.stellar.aldebaran.model.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Number of dumbbells of a line that writes no laterality.
 *
 * @param loadPosition The load position it applies to, empty for a line without one.
 * @param implementCount 1 or 2, or null when the athlete is free to choose.
 */
@Embeddable
public record DumbbellBase(
    @Column(name = "load_position", nullable = false, length = 50) String loadPosition,
    @Column(name = "implement_count") Integer implementCount) {}
