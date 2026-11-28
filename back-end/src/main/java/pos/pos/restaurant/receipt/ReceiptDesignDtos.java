package pos.pos.restaurant.receipt;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

/** The receipts' design as the app sends it, and what's saved ([design] null = never saved: the app starts from its own). */
public final class ReceiptDesignDtos {

    private ReceiptDesignDtos() {
    }

    public record ReceiptDesignResponse(JsonNode design, long version) {
    }

    public record SaveReceiptDesignRequest(@NotNull(message = "design is required") JsonNode design, Long expectedVersion) {
    }

    public record ReceiptPictureResponse(String url) {
    }
}
