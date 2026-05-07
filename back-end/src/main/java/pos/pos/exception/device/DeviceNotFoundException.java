package pos.pos.exception.device;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class DeviceNotFoundException extends AuthException {

    public DeviceNotFoundException() {
        super("Device not found", HttpStatus.NOT_FOUND);
    }
}
