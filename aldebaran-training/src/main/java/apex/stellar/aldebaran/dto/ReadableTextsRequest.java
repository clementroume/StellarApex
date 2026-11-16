package apex.stellar.aldebaran.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Texts of WOD lines to check while a WOD is edited (D121: the form warns, never blocks).
 *
 * @param texts The texts ({@code repsScheme}), in the order of the lines.
 */
public record ReadableTextsRequest(
    @NotNull(message = "{validation.balance.texts.required}")
        @Size(max = 100, message = "{validation.balance.texts.size}")
        List<@Size(max = 50, message = "{validation.balance.text.size}") String> texts) {}
