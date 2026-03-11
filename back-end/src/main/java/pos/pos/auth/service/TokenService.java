package pos.pos.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pos.pos.user.entity.User;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenService {

    public String generateAccessToken(User user) {

        return UUID.randomUUID().toString();

    }

    public String generateRefreshToken(User user) {

        return UUID.randomUUID().toString();

    }

}