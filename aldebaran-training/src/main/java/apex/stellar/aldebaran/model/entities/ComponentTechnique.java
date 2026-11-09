package apex.stellar.aldebaran.model.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * A technique of one component of a chained line (Power Clean and Split Jerk: 1 = POWER, 2 = SPLIT).
 *
 * @param componentOrder The component, from 1.
 * @param technique The technique.
 */
@Embeddable
public record ComponentTechnique(
    @Column(name = "component_order", nullable = false) Integer componentOrder,
    @Column(name = "technique", nullable = false, length = 50) String technique) {}
