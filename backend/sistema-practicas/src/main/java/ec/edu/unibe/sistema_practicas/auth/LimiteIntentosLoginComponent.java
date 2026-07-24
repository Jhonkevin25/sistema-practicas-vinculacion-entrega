package ec.edu.unibe.sistema_practicas.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class LimiteIntentosLoginComponent {

    private static final int MAX_ORIGENES_REGISTRADOS = 10_000;
    private static final String MENSAJE_LIMITE =
            "Demasiados intentos de inicio de sesión. Espera un momento e inténtalo nuevamente.";

    private final ConcurrentHashMap<String, Ventana> ventanas = new ConcurrentHashMap<>();
    private final AtomicLong verificaciones = new AtomicLong();

    @Value("${app.auth.login-rate-max-requests:30}")
    private int maxSolicitudes;

    @Value("${app.auth.login-rate-window-seconds:60}")
    private long ventanaSegundos;

    @Value("${app.auth.trust-forwarded-for:false}")
    private boolean confiarForwardedFor;

    public void verificar(HttpServletRequest request) {
        long ahora = System.currentTimeMillis();
        String origen = resolverOrigen(request);

        if (!ventanas.containsKey(origen) && ventanas.size() >= MAX_ORIGENES_REGISTRADOS) {
            limpiarExpiradas(ahora);
            if (ventanas.size() >= MAX_ORIGENES_REGISTRADOS) {
                throw limiteExcedido();
            }
        }

        long duracion = Math.max(1, ventanaSegundos) * 1_000L;
        Ventana actual = ventanas.compute(origen, (clave, anterior) -> {
            if (anterior == null || ahora - anterior.inicioMillis() >= duracion) {
                return new Ventana(ahora, 1);
            }
            return new Ventana(anterior.inicioMillis(), anterior.solicitudes() + 1);
        });

        if ((verificaciones.incrementAndGet() & 127) == 0) {
            limpiarExpiradas(ahora);
        }
        if (actual.solicitudes() > Math.max(1, maxSolicitudes)) {
            throw limiteExcedido();
        }
    }

    private String resolverOrigen(HttpServletRequest request) {
        if (request == null) {
            return "desconocido";
        }
        if (confiarForwardedFor) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null) {
                String candidato = forwardedFor.split(",", 2)[0].trim();
                if (candidato.length() <= 45 && candidato.matches("[0-9a-fA-F:.]+")) {
                    return candidato;
                }
            }
        }
        String remoto = request.getRemoteAddr();
        return remoto == null || remoto.isBlank() ? "desconocido" : remoto;
    }

    private void limpiarExpiradas(long ahora) {
        long duracion = Math.max(1, ventanaSegundos) * 1_000L;
        ventanas.entrySet().removeIf(entry -> ahora - entry.getValue().inicioMillis() >= duracion);
    }

    private ResponseStatusException limiteExcedido() {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, MENSAJE_LIMITE);
    }

    private record Ventana(long inicioMillis, long solicitudes) {}
}
