package pos.pos.device.network;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * The network a request came from, as one short text to compare (user, 2026-11-24: "only in the restaurant's network").
 * Every device on a restaurant's Wi-Fi reaches the server through the same internet address, so a public IPv4 address is
 * kept whole. An IPv6 address is different for each device, but the first half (the /64) is the same on one network, so
 * that's what's kept. A private address (the server inside the restaurant itself, or a test on a home network) stands for
 * its /24. Nothing is looked up: only written-out addresses are read.
 */
public final class NetworkAddress {

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9a-fA-F:.]+$");

    private NetworkAddress() {
    }

    /**
     * Whether this computer itself (127.0.0.1, ::1) counts as the network it sits on (`app.devices.network.loopback-is-this-network`,
     * on in the local profile; user, 2026-11-25: trying a till on a second computer). Without it a till on the server's own
     * computer saves "127.0.0.1" and a till next to it on the same Wi-Fi is "another network". In production nothing comes from
     * 127.0.0.1 (the proxy sends the real address), so it stays off there and in tests.
     */
    private static volatile boolean loopbackIsThisNetwork;
    private static volatile String home;
    private static volatile long homeCheckedAt;

    public static void loopbackIsThisNetwork(boolean on) {
        loopbackIsThisNetwork = on;
    }

    /** This computer's own network ("192.168.178.0/24"), from the address it would use to go out; looked at once a minute. */
    private static String loopback() {
        if (!loopbackIsThisNetwork) return "127.0.0.1";
        long now = System.currentTimeMillis();
        if (homeCheckedAt == 0 || now - homeCheckedAt > 60_000) {
            String found = null;
            // A UDP "connect" sends nothing: it only asks the system which address it would go out from.
            try (java.net.DatagramSocket socket = new java.net.DatagramSocket()) {
                socket.connect(InetAddress.getByName("8.8.8.8"), 53);
                if (socket.getLocalAddress() instanceof Inet4Address own && own.isSiteLocalAddress()) found = ofV4(own);
            } catch (Exception ignored) {
                // No network: this computer stays itself.
            }
            home = found;
            homeCheckedAt = now;
        }
        return home != null ? home : "127.0.0.1";
    }

    /** "85.12.34.56", "2a02:1810:4d2:1200::/64", "192.168.178.0/24"; null when there's no address or it isn't one. */
    public static String of(String ip) {
        if (ip == null) return null;
        String text = ip.trim();
        int zone = text.indexOf('%');
        if (zone >= 0) text = text.substring(0, zone);
        if (text.startsWith("[") && text.endsWith("]")) text = text.substring(1, text.length() - 1);
        if (text.isEmpty() || text.length() > 45) return null;
        boolean v4 = IPV4.matcher(text).matches();
        if (!v4 && !(text.contains(":") && IPV6.matcher(text).matches())) return null;
        InetAddress address;
        try {
            address = InetAddress.getByName(text);
        } catch (UnknownHostException e) {
            return null;
        }
        if (address instanceof Inet4Address v4Address) return ofV4(v4Address);
        if (address instanceof Inet6Address v6Address) return ofV6(v6Address);
        return null;
    }

    private static String ofV4(Inet4Address address) {
        byte[] b = address.getAddress();
        int a0 = b[0] & 0xff, a1 = b[1] & 0xff, a2 = b[2] & 0xff, a3 = b[3] & 0xff;
        if (address.isLoopbackAddress()) return loopback();
        if (address.isSiteLocalAddress() || address.isLinkLocalAddress()) return a0 + "." + a1 + "." + a2 + ".0/24";
        return a0 + "." + a1 + "." + a2 + "." + a3;
    }

    private static String ofV6(Inet6Address address) {
        // This computer, however it was reached (dev, or a till on the server's own machine).
        if (address.isLoopbackAddress()) return loopback();
        byte[] b = address.getAddress();
        StringBuilder out = new StringBuilder();
        for (int group = 0; group < 4; group++) {
            int value = ((b[group * 2] & 0xff) << 8) | (b[group * 2 + 1] & 0xff);
            if (group > 0) out.append(':');
            out.append(Integer.toHexString(value));
        }
        return out.append("::/64").toString();
    }
}
