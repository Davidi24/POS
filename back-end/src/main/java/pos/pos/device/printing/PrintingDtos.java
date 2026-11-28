package pos.pos.device.printing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** What each printer prints and how the app reaches printers (user, 2026-11-27). */
public final class PrintingDtos {

    private PrintingDtos() {
    }

    /** A printer as the app sends to it: over the network, the computer's printer list, or Bluetooth (by its address). */
    public record Printer(UUID id, String name, String role, String connectionType, String ip, Integer port, String systemPrinterName,
                          UUID hostDeviceId, String hostDeviceName, int paperWidthMm, boolean autoCut, boolean cashDrawerKick, boolean usable) {
    }

    /** A till or kitchen station a printer can print for, and the printer it prints on now (none: null). */
    public record Target(UUID id, String name, String branchName, UUID printerId, String printerName) {
    }

    /** What a printer prints (its page): the tills whose receipts and bills, the kitchen stations whose tickets. */
    public record PrinterJobs(List<UUID> receiptTillIds, List<UUID> kitchenStationIds, List<Target> tills, List<Target> stations) {
    }

    public record SetPrinterJobsRequest(@NotNull @Size(max = 200) List<@NotNull UUID> receiptTillIds,
                                        @NotNull @Size(max = 200) List<@NotNull UUID> kitchenStationIds) {
    }

    // How the station's tickets print: letter size NORMAL / BIG / HUGE, notes in red (decided 2026-11-28).
    public record StationPrinters(UUID stationId, String stationName, List<Printer> printers, String textSize, boolean notesRed) {
    }

    /**
     * What the device the app runs on needs to print: its receipt printer (a till's), each kitchen station's printer, every
     * printer of the restaurant, and when things print by themselves.
     */
    public record Setup(UUID deviceId, List<Printer> receiptPrinters, List<StationPrinters> kitchen, List<Printer> printers,
                        boolean printKitchenOnSend, boolean printBillOnRequest, String printReceiptAfterPayment) {
    }

    /** A print left for the device a printer is plugged into: the bytes as Base64. */
    public record QueueRequest(@NotBlank @Size(max = 1_400_000) String data) {
    }

    public record QueuedPrint(UUID id, UUID printerId, String status, String problem, OffsetDateTime createdAt) {
    }

    /** What the device a printer is plugged into has to print. */
    public record HostJob(UUID id, UUID printerId, String connectionType, String systemPrinterName, String data) {
    }

    public record HostResult(boolean ok, @Size(max = 300) String problem) {
    }

    /** A printer a till checks once a minute: on the network (by its address) or plugged into that till (by its name). */
    public record PrinterToCheck(UUID id, String name, String role, String connectionType, String ip, Integer port, String systemPrinterName) {
    }

    public record PrinterCheck(@NotNull UUID printerId, boolean reachable, @Size(max = 300) String problem) {
    }

    public record PrinterChecksRequest(@NotNull @Size(max = 50) List<@Valid PrinterCheck> checks) {
    }
}
