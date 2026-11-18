package apex.stellar.aldebaran.model.entities;

import apex.stellar.aldebaran.model.enums.MovementPattern;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Stores a set of movement patterns as space-separated names, in declaration order. */
@Converter
public class PatternSetConverter implements AttributeConverter<Set<MovementPattern>, String> {

  @Override
  public String convertToDatabaseColumn(Set<MovementPattern> values) {
    if (values == null || values.isEmpty()) {
      return "";
    }
    return EnumSet.copyOf(values).stream().map(Enum::name).collect(Collectors.joining(" "));
  }

  @Override
  public Set<MovementPattern> convertToEntityAttribute(String column) {
    Set<MovementPattern> patterns = EnumSet.noneOf(MovementPattern.class);
    if (column != null && !column.isBlank()) {
      Arrays.stream(column.trim().split("\\s+")).map(MovementPattern::valueOf).forEach(patterns::add);
    }
    return patterns;
  }
}
