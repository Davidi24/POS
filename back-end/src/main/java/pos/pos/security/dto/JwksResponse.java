package pos.pos.security.dto;

import java.util.List;

public record JwksResponse(List<JwkKeyResponse> keys) {

    public record JwkKeyResponse(
            String kty,
            String use,
            String alg,
            String kid,
            String n,
            String e
    ) {
    }
}
