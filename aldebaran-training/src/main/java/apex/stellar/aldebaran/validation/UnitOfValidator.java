package apex.stellar.aldebaran.validation;

import apex.stellar.aldebaran.model.enums.Unit;
import apex.stellar.aldebaran.model.enums.UnitField;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Checks {@link UnitOf}: the unit belongs to the kind of the field. */
public class UnitOfValidator implements ConstraintValidator<UnitOf, Unit> {

  private UnitField field;

  @Override
  public void initialize(UnitOf annotation) {
    this.field = annotation.value();
  }

  @Override
  public boolean isValid(Unit unit, ConstraintValidatorContext context) {
    return field.accepts(unit);
  }
}
