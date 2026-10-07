package pos.pos.order.dto;
import java.util.List;
public record OrderHistoryPage(List<OrderResponse> items, int page, int size, long totalElements, int totalPages, boolean hasNext) {}
