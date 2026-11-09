package apex.stellar.aldebaran.model.entities;

import apex.stellar.aldebaran.model.enums.JointAction;
import apex.stellar.aldebaran.model.enums.JointAction.Regime;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * What a joint does in a movement.
 *
 * @param trigger Empty for the base movement, else the technique or equipment that brings it.
 * @param action The joint action.
 * @param regime Moved under load, or held.
 */
@Embeddable
public record MovementJointAction(
    @Column(name = "trigger", nullable = false, length = 50) String trigger,
    @Enumerated(EnumType.STRING) @Column(name = "action", nullable = false, length = 20)
        JointAction action,
    @Enumerated(EnumType.STRING) @Column(name = "regime", nullable = false, length = 10)
        Regime regime) {}
