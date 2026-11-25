package pos.pos.device.network;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Passes `app.devices.network.loopback-is-this-network` to [NetworkAddress] (on in the local profile only). */
@Component
class NetworkAddressSettings {

    NetworkAddressSettings(@Value("${app.devices.network.loopback-is-this-network:false}") boolean loopbackIsThisNetwork) {
        NetworkAddress.loopbackIsThisNetwork(loopbackIsThisNetwork);
    }
}
