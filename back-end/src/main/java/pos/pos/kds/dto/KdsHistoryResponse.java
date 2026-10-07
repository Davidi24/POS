package pos.pos.kds.dto;

import java.util.List;

public record KdsHistoryResponse(List<KdsTicketResponse> items, int page, int size,
                                 long totalElements, int totalPages, boolean hasNext) {}
