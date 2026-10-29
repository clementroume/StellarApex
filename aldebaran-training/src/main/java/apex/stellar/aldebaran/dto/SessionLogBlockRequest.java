package apex.stellar.aldebaran.dto;

/**
 * DTO for checking or unchecking a block of a journal.
 *
 * @param done Whether the athlete did the block.
 */
public record SessionLogBlockRequest(boolean done) {}
