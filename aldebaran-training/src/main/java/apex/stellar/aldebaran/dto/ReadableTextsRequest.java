package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Texts of WOD lines to check while a WOD is edited (D121: the form warns, never blocks), with what
 * of the WOD decides how a line counts.
 *
 * @param texts The texts ({@code repsScheme}), in the order of the lines.
 * @param wodType The format (For Load counts repetitions), or null.
 * @param scoreType The score type (scored by load counts repetitions), or null.
 * @param ladderStart Start of the WOD's ladder (a Death by line without readable text climbs it), or null.
 * @param ladderStep Step of the ladder, or null.
 * @param repMax The "N RM" of the WOD (a strength line without readable text counts N), or null.
 */
public record ReadableTextsRequest(
    @NotNull(message = "{validation.balance.texts.required}")
        @Size(max = 100, message = "{validation.balance.texts.size}")
        List<@Size(max = 50, message = "{validation.balance.text.size}") String> texts,
    @Nullable WodType wodType,
    @Nullable ScoreType scoreType,
    @Nullable Integer ladderStart,
    @Nullable Integer ladderStep,
    @Nullable Integer repMax) {}
