package ec.edu.unibe.sistema_practicas.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LimiteIntentosLoginComponentTests {

    private LimiteIntentosLoginComponent limite;

    @BeforeEach
    void configurar() {
        limite = new LimiteIntentosLoginComponent();
        ReflectionTestUtils.setField(limite, "maxSolicitudes", 2);
        ReflectionTestUtils.setField(limite, "ventanaSegundos", 60L);
        ReflectionTestUtils.setField(limite, "confiarForwardedFor", false);
    }

    @Test
    void rechaza_solicitudes_que_superan_el_limite_del_origen() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");

        assertDoesNotThrow(() -> limite.verificar(request));
        assertDoesNotThrow(() -> limite.verificar(request));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> limite.verificar(request));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatusCode());
    }

    @Test
    void no_confia_en_forwarded_for_si_no_se_habilita_expresamente() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.20");
        request.addHeader("X-Forwarded-For", "203.0.113.1");

        limite.verificar(request);
        request.removeHeader("X-Forwarded-For");
        request.addHeader("X-Forwarded-For", "203.0.113.2");
        limite.verificar(request);

        assertThrows(ResponseStatusException.class, () -> limite.verificar(request));
    }
}
