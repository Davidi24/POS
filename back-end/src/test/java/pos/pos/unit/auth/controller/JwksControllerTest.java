package pos.pos.unit.auth.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pos.pos.auth.controller.JwksController;
import pos.pos.security.dto.JwksResponse;
import pos.pos.security.service.JwtService;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwksController")
class JwksControllerTest {

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private JwksController controller;

    @Test
    @DisplayName("Should expose the public key in JWKS format")
    void shouldExposeJwksDocument() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        JwksResponse response = new JwksResponse(List.of(
                new JwksResponse.JwkKeyResponse("RSA", "sig", "RS256", "test-kid", "test-n", "AQAB")
        ));
        given(jwtService.getJwks()).willReturn(response);

        mockMvc.perform(get("/auth/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].use").value("sig"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].kid").value("test-kid"))
                .andExpect(jsonPath("$.keys[0].n").value("test-n"))
                .andExpect(jsonPath("$.keys[0].e").value("AQAB"));
    }
}
