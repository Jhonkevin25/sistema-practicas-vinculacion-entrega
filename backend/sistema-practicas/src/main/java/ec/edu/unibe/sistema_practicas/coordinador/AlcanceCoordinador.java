package ec.edu.unibe.sistema_practicas.coordinador;

import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// Apoyo compartido para que los listados apliquen el alcance por carrera del
// coordinador tambien en el backend (el frontend ya filtra, esto lo garantiza)
@Component
@RequiredArgsConstructor
public class AlcanceCoordinador {

    private final CoordinadorCarreraRepository coordinadorRepository;

    // Para un COORDINADOR siempre se devuelve un Optional presente. Un
    // conjunto vacio significa que no tiene alcance y, por tanto, no puede
    // consultar ni gestionar carreras. Los demas roles no se restringen aqui.
    public Optional<Set<String>> carrerasVisibles(Authentication authentication) {
        if (!esCoordinador(authentication)) {
            return Optional.empty();
        }
        if (!(authentication.getPrincipal() instanceof Usuario usuario)) {
            return Optional.of(Set.of());
        }
        Set<String> carreras = coordinadorRepository.findByUsuarioId(usuario.getId()).stream()
                .map(CoordinadorCarrera::getCarrera)
                .collect(Collectors.toSet());
        return Optional.of(carreras);
    }

    // El tipo de coordinacion es una frontera de autorizacion del backend, no
    // solo una preferencia visual. Configuraciones ausentes o inconsistentes
    // fallan cerradas para evitar acceso accidental al otro proceso.
    public boolean procesoVisible(Authentication authentication, String proceso) {
        if (!esCoordinador(authentication)) {
            return true;
        }
        if (!(authentication.getPrincipal() instanceof Usuario usuario)) {
            return false;
        }
        String procesoNormalizado = normalizar(proceso);
        if (!Set.of("PRACTICAS", "VINCULACION").contains(procesoNormalizado)) {
            return false;
        }

        List<CoordinadorCarrera> filas = coordinadorRepository.findByUsuarioId(usuario.getId());
        if (filas.isEmpty()) {
            return false;
        }
        String tipoConfigurado = null;
        for (CoordinadorCarrera fila : filas) {
            String tipoFila = normalizar(fila.getCoordinacionTipo());
            if (!Set.of("PRACTICAS", "VINCULACION", "AMBOS").contains(tipoFila)
                    || (tipoConfigurado != null && !tipoConfigurado.equals(tipoFila))) {
                return false;
            }
            tipoConfigurado = tipoFila;
        }
        return "AMBOS".equals(tipoConfigurado) || procesoNormalizado.equals(tipoConfigurado);
    }

    private boolean esCoordinador(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_COORDINADOR".equals(a.getAuthority()));
    }

    private String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
    }
}
