package pos.pos.device.network;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import pos.pos.auth.service.AccountAudit;
import pos.pos.device.entity.Device;
import pos.pos.device.enums.DeviceType;
import pos.pos.device.network.DeviceNetworkDtos.DeviceNetworkView;
import pos.pos.device.network.DeviceNetworkDtos.NetworkLine;
import pos.pos.device.network.DeviceNetworkDtos.RefusedLine;
import pos.pos.device.service.ConnectedDeviceService;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.auth.LoginRefusedException;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.rbac.AppRole;
import pos.pos.settings.service.SettingsAuditService;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tills, tablets and screens only on the restaurant's network (user, 2026-11-24: "they can be configured only when they're in
 * the restaurant's network"). Each branch keeps up to [MAX_NETWORKS] networks ([NetworkAddress]) and a switch. While it's on:
 * <ul>
 *   <li>connecting one of those devices with a pairing code works only from a saved network, unless the code was made by an
 *   owner, a co-owner or a platform admin (they can connect from anywhere, so nobody is ever locked out);</li>
 *   <li>a PIN sign-in on a till works only from a saved network (a password sign-in isn't touched).</li>
 * </ul>
 * The first device connected at a branch saves its network by itself and switches the rule on; after that only people with
 * DEVICES_MANAGE change it (Settings → Devices & printing). A device turned away is written down for a week, so the owner sees
 * the new network (an internet address can change) and can allow it from anywhere.
 */
@Service
@RequiredArgsConstructor
public class DeviceNetworkService {

    public static final int MAX_NETWORKS = 5;
    /** How long a turned-away network stays listed, and may be allowed from another network. */
    static final Duration REFUSALS_KEPT = Duration.ofDays(7);
    private static final int REFUSED_SHOWN = 10;

    /** The devices this is about: tills, tablets and the kitchen and guest screens (not card terminals or printers). */
    /** Tills, tablets and screens: the devices the network rule (and "Connect devices") is about. */
    public static final Set<DeviceType> RULED = EnumSet.of(DeviceType.POS_TERMINAL, DeviceType.TABLET, DeviceType.KDS, DeviceType.CUSTOMER_DISPLAY);

    private static final Set<String> CONNECT_FROM_ANYWHERE = Set.of(
            AppRole.SUPER_ADMIN.name(), AppRole.CO_SUPER_ADMIN.name(), AppRole.OWNER.name(), AppRole.CO_OWNER.name());

    static final String AUDIT_ENTITY = "DEVICE_NETWORK";

    private final DeviceNetworkRuleRepository rules;
    private final DeviceNetworkRepository networks;
    private final DeviceNetworkRefusalRepository refusals;
    private final RestaurantScopeService restaurantScopeService;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final SettingsAuditService settingsAudit;
    private final AccountAudit accountAudit;

    // ---- Settings → Devices & printing ----

    @Transactional(readOnly = true)
    public DeviceNetworkView view(Authentication authentication, UUID restaurantId, UUID branchId, String callerIp) {
        Branch branch = restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        return viewOf(branch, NetworkAddress.of(callerIp));
    }

    @Transactional
    public DeviceNetworkView setRule(Authentication authentication, UUID restaurantId, UUID branchId, boolean on, String callerIp) {
        Branch branch = restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        UUID actor = restaurantScopeService.currentUserId(authentication);
        DeviceNetworkRule rule = rules.findForUpdate(branchId).orElseGet(() -> newRule(branch));
        if (on && networks.countByBranchId(branchId) == 0) {
            throw new AuthException("Save the restaurant's network first.", HttpStatus.BAD_REQUEST);
        }
        if (rule.isOnlyOnNetwork() != on || rule.getUpdatedAt() == null) {
            rule.setOnlyOnNetwork(on);
            touch(rule, actor);
            rules.save(rule);
            settingsAudit.log(branch.getRestaurant(), branch, AUDIT_ENTITY, null, "UPDATE",
                    on ? "Tills, tablets and screens now work only on " + branch.getName() + "'s network"
                            : "Tills, tablets and screens now work on any network at " + branch.getName(), actor);
        }
        return viewOf(branch, NetworkAddress.of(callerIp));
    }

    @Transactional
    public DeviceNetworkView add(Authentication authentication, UUID restaurantId, UUID branchId, String address, String callerIp) {
        Branch branch = restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        UUID actor = restaurantScopeService.currentUserId(authentication);
        OffsetDateTime now = now();
        String yours = NetworkAddress.of(callerIp);
        String wanted;
        if (!StringUtils.hasText(address)) {
            if (yours == null) throw new AuthException("Couldn't tell which network you're on.", HttpStatus.BAD_REQUEST);
            wanted = yours;
        } else {
            wanted = address.trim();
            // From another network, only one a device of this branch was turned away from lately (nobody types addresses).
            if (!wanted.equals(yours) && !refusals.existsByBranchIdAndAddressAndRefusedAtAfter(branchId, wanted, now.minus(REFUSALS_KEPT))) {
                throw new AuthException("Only a network a device tried from can be allowed from here.", HttpStatus.BAD_REQUEST);
            }
        }
        DeviceNetworkRule rule = rules.findForUpdate(branchId).orElse(null);
        if (networks.findByBranchIdAndAddress(branchId, wanted).isPresent()) {
            return viewOf(branch, yours);
        }
        long saved = networks.countByBranchId(branchId);
        if (saved >= MAX_NETWORKS) {
            throw new AuthException("A branch keeps " + MAX_NETWORKS + " networks at most. Remove one first.", HttpStatus.CONFLICT);
        }
        DeviceNetwork network = new DeviceNetwork();
        network.setId(UUID.randomUUID());
        network.setBranchId(branchId);
        network.setAddress(wanted);
        network.setAddedBy(actor);
        network.setAddedAt(now);
        networks.save(network);
        refusals.forgetAddress(branchId, wanted);
        // The first network switches the rule on (user, 2026-11-24: "the switch is on once a network is saved").
        boolean switchedOn = false;
        if (rule == null || (saved == 0 && !rule.isOnlyOnNetwork())) {
            if (rule == null) rule = newRule(branch);
            rule.setOnlyOnNetwork(true);
            touch(rule, actor);
            rules.save(rule);
            switchedOn = true;
        }
        settingsAudit.log(branch.getRestaurant(), branch, AUDIT_ENTITY, network.getId(), "CREATE",
                "Added the network " + wanted + " to " + branch.getName() + (switchedOn ? "; tills, tablets and screens now work only there" : ""), actor);
        return viewOf(branch, yours);
    }

    @Transactional
    public DeviceNetworkView remove(Authentication authentication, UUID restaurantId, UUID branchId, UUID networkId, String callerIp) {
        Branch branch = restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        UUID actor = restaurantScopeService.currentUserId(authentication);
        DeviceNetworkRule rule = rules.findForUpdate(branchId).orElse(null);
        DeviceNetwork network = networks.findByIdAndBranchId(networkId, branchId)
                .orElseThrow(() -> new AuthException("That network isn't saved any more.", HttpStatus.NOT_FOUND));
        networks.delete(network);
        networks.flush();
        // With no network left the rule can't hold: devices work anywhere until one is saved again.
        boolean switchedOff = false;
        if (rule != null && rule.isOnlyOnNetwork() && networks.countByBranchId(branchId) == 0) {
            rule.setOnlyOnNetwork(false);
            touch(rule, actor);
            rules.save(rule);
            switchedOff = true;
        }
        settingsAudit.log(branch.getRestaurant(), branch, AUDIT_ENTITY, network.getId(), "DELETE",
                "Removed the network " + network.getAddress() + " from " + branch.getName()
                        + (switchedOff ? "; tills, tablets and screens now work on any network" : ""), actor);
        return viewOf(branch, NetworkAddress.of(callerIp));
    }

    // ---- The checks (called inside the pairing's and the PIN sign-in's own transactions) ----

    /** What [beforeConnect] found: the device's network, whether it was outside (an owner's code), whether it was saved now. */
    public record ConnectCheck(String network, boolean outside, boolean learned) {
    }

    /**
     * Before a pairing code connects [device] (its row is locked): fine from a saved network or with the rule off; the
     * branch's first device saves its network; from outside only with a code an owner or a platform admin made, otherwise
     * written down and refused. Runs in the caller's transaction, which must not roll back on [OutsideNetworkException].
     */
    public ConnectCheck beforeConnect(Device device, UUID codeMadeBy, String ip) {
        return beforeConnect(device, codeMadeBy, null, ip);
    }

    /**
     * The same, for a code typed by someone signed in on the device ([enteredBy], 2026-11-25): an owner (or platform admin)
     * making the code or typing it connects from anywhere.
     */
    @Transactional
    public ConnectCheck beforeConnect(Device device, UUID codeMadeBy, UUID enteredBy, String ip) {
        String network = NetworkAddress.of(ip);
        Branch branch = device.getBranch();
        if (branch == null || !RULED.contains(device.getDeviceType())) return new ConnectCheck(network, false, false);
        UUID branchId = branch.getId();
        OffsetDateTime now = now();
        DeviceNetworkRule rule = rules.findForUpdate(branchId).orElse(null);
        if (rule == null) {
            if (network == null) return new ConnectCheck(null, false, false);
            learn(branch, network, device, codeMadeBy, now);
            return new ConnectCheck(network, false, true);
        }
        DeviceNetwork match = network == null ? null : networks.findByBranchIdAndAddress(branchId, network).orElse(null);
        if (match != null) {
            match.setLastSeenAt(now);
            networks.save(match);
            return new ConnectCheck(network, false, false);
        }
        if (!rule.isOnlyOnNetwork()) return new ConnectCheck(network, false, false);
        if (mayConnectFromAnywhere(codeMadeBy) || (enteredBy != null && mayConnectFromAnywhere(enteredBy))) return new ConnectCheck(network, true, false);
        refuse(branchId, network, device.getId(), device.getName(), DeviceNetworkRefusal.CONNECT, now);
        accountAudit.inRestaurant(device.getRestaurant().getId(), device.getId(), codeMadeBy, "OUTSIDE_NETWORK",
                AccountAudit.deviceOf(device.getName()) + " couldn't be connected: it was outside the restaurant's network ("
                        + (network == null ? "unknown" : network) + ")");
        throw new OutsideNetworkException("Connect this device from the restaurant's Wi-Fi.");
    }

    /**
     * Before a PIN sign-in on [device]: with the rule on, only from one of its branch's networks; otherwise written down and
     * refused with OUTSIDE_NETWORK (403). Runs in the sign-in's transaction (which keeps what refusals write).
     */
    public void beforePin(ConnectedDeviceService.Connected device, String ip) {
        if (device.branchId() == null) return;
        DeviceNetworkRule rule = rules.findById(device.branchId()).orElse(null);
        String network = NetworkAddress.of(ip);
        DeviceNetwork match = network == null ? null : networks.findByBranchIdAndAddress(device.branchId(), network).orElse(null);
        OffsetDateTime now = now();
        if (match != null) {
            match.setLastSeenAt(now);
            networks.save(match);
            return;
        }
        if (rule == null || !rule.isOnlyOnNetwork()) return;
        refuse(device.branchId(), network, device.deviceId(), device.name(), DeviceNetworkRefusal.PIN, now);
        accountAudit.inRestaurant(device.restaurantId(), device.deviceId(), null, "OUTSIDE_NETWORK",
                "A PIN sign-in on " + AccountAudit.deviceOf(device.name()) + " was refused: it was outside the restaurant's network ("
                        + (network == null ? "unknown" : network) + ")");
        throw new LoginRefusedException(LoginRefusedException.Reason.OUTSIDE_NETWORK,
                "This till works only on the restaurant's Wi-Fi.", HttpStatus.FORBIDDEN, null);
    }

    // ---- Inside ----

    private void learn(Branch branch, String network, Device device, UUID by, OffsetDateTime now) {
        DeviceNetworkRule rule = newRule(branch);
        rule.setOnlyOnNetwork(true);
        rule.setUpdatedAt(now);
        rule.setUpdatedBy(by);
        rules.save(rule);
        DeviceNetwork saved = new DeviceNetwork();
        saved.setId(UUID.randomUUID());
        saved.setBranchId(branch.getId());
        saved.setAddress(network);
        saved.setLearnedFromDeviceId(device.getId());
        saved.setLearnedFromName(device.getName());
        saved.setAddedBy(by);
        saved.setAddedAt(now);
        saved.setLastSeenAt(now);
        networks.save(saved);
        settingsAudit.log(branch.getRestaurant(), branch, AUDIT_ENTITY, saved.getId(), "CREATE",
                branch.getName() + "'s network is now " + network + ", saved when " + AccountAudit.deviceOf(device.getName())
                        + " was connected; tills, tablets and screens work only there", by);
    }

    private void refuse(UUID branchId, String network, UUID deviceId, String deviceName, String kind, OffsetDateTime now) {
        refusals.forgetBefore(branchId, now.minus(REFUSALS_KEPT));
        DeviceNetworkRefusal refusal = new DeviceNetworkRefusal();
        refusal.setId(UUID.randomUUID());
        refusal.setBranchId(branchId);
        refusal.setAddress(network == null ? "unknown" : network);
        refusal.setDeviceId(deviceId);
        refusal.setDeviceName(deviceName == null ? null : deviceName.length() > 150 ? deviceName.substring(0, 150) : deviceName);
        refusal.setKind(kind);
        refusal.setRefusedAt(now);
        refusals.save(refusal);
    }

    private boolean mayConnectFromAnywhere(UUID userId) {
        if (userId == null) return false;
        return roleRepository.findActiveRoleCodesByUserId(userId).stream().anyMatch(CONNECT_FROM_ANYWHERE::contains);
    }

    private DeviceNetworkRule newRule(Branch branch) {
        DeviceNetworkRule rule = new DeviceNetworkRule();
        rule.setBranchId(branch.getId());
        rule.setRestaurantId(branch.getRestaurant().getId());
        return rule;
    }

    private static void touch(DeviceNetworkRule rule, UUID actor) {
        rule.setUpdatedAt(now());
        rule.setUpdatedBy(actor);
    }

    private DeviceNetworkView viewOf(Branch branch, String yours) {
        UUID branchId = branch.getId();
        boolean on = rules.findById(branchId).map(DeviceNetworkRule::isOnlyOnNetwork).orElse(false);
        List<DeviceNetwork> saved = networks.findByBranchIdOrderByAddedAtAsc(branchId);
        Set<UUID> people = saved.stream().map(DeviceNetwork::getAddedBy).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> names = people.isEmpty() ? Map.of() : userRepository.findAllById(people).stream()
                .collect(Collectors.toMap(User::getId, AccountAudit::nameOf, (a, b) -> a));
        List<NetworkLine> lines = saved.stream().map(n -> new NetworkLine(n.getId(), n.getAddress(), n.getAddedAt(),
                n.getAddedBy() == null ? null : names.getOrDefault(n.getAddedBy(), "Someone"), n.getLearnedFromName(), n.getLastSeenAt(),
                n.getAddress().equals(yours))).toList();
        Set<String> savedAddresses = saved.stream().map(DeviceNetwork::getAddress).collect(Collectors.toSet());

        // Turned-away networks of the last week, the latest first, one line per network.
        Map<String, List<DeviceNetworkRefusal>> byAddress = new LinkedHashMap<>();
        refusals.findTop200ByBranchIdAndRefusedAtAfterOrderByRefusedAtDesc(branchId, now().minus(REFUSALS_KEPT)).stream()
                .filter(r -> !savedAddresses.contains(r.getAddress()))
                .forEach(r -> byAddress.computeIfAbsent(r.getAddress(), key -> new ArrayList<>()).add(r));
        List<RefusedLine> refused = byAddress.entrySet().stream().limit(REFUSED_SHOWN).map(entry -> {
            List<DeviceNetworkRefusal> tries = entry.getValue();
            List<String> devices = tries.stream().map(DeviceNetworkRefusal::getDeviceName).filter(StringUtils::hasText)
                    .collect(Collectors.toCollection(LinkedHashSet::new)).stream().limit(3).toList();
            return new RefusedLine(entry.getKey(), tries.get(0).getRefusedAt(), tries.size(), devices,
                    tries.stream().anyMatch(r -> DeviceNetworkRefusal.CONNECT.equals(r.getKind())),
                    tries.stream().anyMatch(r -> DeviceNetworkRefusal.PIN.equals(r.getKind())));
        }).toList();

        return new DeviceNetworkView(branchId, branch.getName(), on, yours, yours != null && savedAddresses.contains(yours), MAX_NETWORKS,
                lines, refused);
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
