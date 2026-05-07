package pos.pos.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptPreviewResponse {

    private Integer receiptCopies;
    private Boolean showLogo;
    private Boolean showQrCode;
    private List<String> previewLines;
}
