package apex.stellar.aldebaran.model.entities;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Stores a small set of catalog codes as one space-separated column. */
@Converter
public class StringSetConverter implements AttributeConverter<Set<String>, String> {

  @Override
  public String convertToDatabaseColumn(Set<String> values) {
    return values == null ? "" : String.join(" ", values);
  }

  @Override
  public Set<String> convertToEntityAttribute(String column) {
    if (column == null || column.isBlank()) {
      return new LinkedHashSet<>();
    }
    return Arrays.stream(column.trim().split("\\s+"))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
