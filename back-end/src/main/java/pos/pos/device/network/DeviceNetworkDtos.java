package pos.pos.device.network;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Settings → Devices & printing → the restaurant's network (user, 2026-11-24). */
public final class DeviceNetworkDtos {

    private DeviceNetworkDtos() {
    }

    /**
     * The branch's switch, its networks, the network the person asking is on now ([yourNetwork], null when it can't be
     * told), and the networks devices were turned away from in the last week.
     */
    public record DeviceNetworkView(
            UUID branchId,
            String branchName,
            boolean onlyOnNetwork,
            String yourNetwork,
            boolean yourNetworkSaved,
            int maxNetworks,
            List<NetworkLine> networks,
            List<RefusedLine> refused
    ) {
    }

    /** A saved network: who added it, or the device it was saved with ("learnedFrom"), and when a device last used it. */
    public record NetworkLine(UUID id, String address, OffsetDateTime addedAt, String addedBy, String learnedFrom,
                              OffsetDateTime lastSeenAt, boolean yours) {
    }

    /** A network devices tried from: the last time, how many times, which devices, and whether to connect or sign in. */
    public record RefusedLine(String address, OffsetDateTime lastAt, int times, List<String> devices, boolean connecting,
                              boolean pinSignIn) {
    }

    public record SetRuleRequest(@NotNull Boolean onlyOnNetwork) {
    }

    /** No address = the network the person is on now; else one devices were turned away from in the last week. */
    public record AddNetworkRequest(@Size(max = 64) String address) {
    }
}
