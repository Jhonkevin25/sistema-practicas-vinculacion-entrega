package ec.edu.unibe.sistema_practicas.coordinador;

import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import ec.edu.unibe.sistema_practicas.usuario.UsuarioRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/coordinadores")
@RequiredArgsConstructor
public class CoordinadorCarreraController {

    private static final Set<String> TIPOS_VALIDOS = Set.of("PRACTICAS", "VINCULACION", "AMBOS");

    private final CoordinadorCarreraRepository coordinadorRepository;
    private final UsuarioRepository usuarioRepository;

    @Data
    static class Alcance {
        private String tipo;
        private List<String> carreras;
    }

    private Alcance alcanceDe(Integer usuarioId) {
        List<CoordinadorCarrera> filas = coordinadorRepository.findByUsuarioId(usuarioId);
        Alcance alcance = new Alcance();
        alcance.setTipo(filas.isEmpty() ? "AMBOS" : filas.get(0).getCoordinacionTipo());
        alcance.setCarreras(filas.stream().map(CoordinadorCarrera::getCarrera).toList());
        return alcance;
    }

    // El alcance personal es de solo lectura. La configuracion pertenece a
    // ADMIN y se realiza sobre el id del coordinador objetivo.
    @GetMapping("/alcance/me")
    public Alcance getMiAlcance(@AuthenticationPrincipal Usuario usuario) {
        return alcanceDe(usuario.getId());
    }

    @GetMapping("/{usuarioId}/alcance")
    public Alcance getAlcance(@PathVariable Integer usuarioId) {
        exigirCoordinador(usuarioId);
        return alcanceDe(usuarioId);
    }

    @PutMapping("/{usuarioId}/alcance")
    @Transactional
    public Alcance setAlcance(@PathVariable Integer usuarioId,
                             @RequestBody Alcance alcance) {
        Usuario coordinador = exigirCoordinador(usuarioId);

        String tipo = alcance.getTipo() == null
                ? null
                : alcance.getTipo().trim().toUpperCase(Locale.ROOT);
        if (!TIPOS_VALIDOS.contains(tipo)) {
            throw new IllegalArgumentException("El tipo de coordinación debe ser PRACTICAS, VINCULACION o AMBOS.");
        }
        List<String> carreras = alcance.getCarreras() == null
                ? List.of()
                : alcance.getCarreras().stream()
                        .filter(carrera -> carrera != null && !carrera.isBlank())
                        .map(String::trim)
                        .distinct()
                        .toList();
        if (carreras.isEmpty()) {
            throw new IllegalArgumentException("Debes indicar al menos una carrera asignada.");
        }

        coordinadorRepository.deleteByUsuarioId(usuarioId);
        // Sin flush, Hibernate emite los INSERT antes que los DELETE y choca
        // con UNIQUE(usuario_id, carrera) cuando una carrera se conserva
        coordinadorRepository.flush();
        for (String carrera : carreras) {
            CoordinadorCarrera fila = new CoordinadorCarrera();
            fila.setUsuario(coordinador);
            fila.setCarrera(carrera);
            fila.setCoordinacionTipo(tipo);
            coordinadorRepository.save(fila);
        }
        return alcanceDe(usuarioId);
    }

    private Usuario exigirCoordinador(Integer usuarioId) {
        Usuario coordinador = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Usuario coordinador no encontrado."));
        boolean tieneRolCoordinador = coordinador.getRoles() != null
                && coordinador.getRoles().stream()
                        .anyMatch(rol -> "COORDINADOR".equals(rol.getCodigo()));
        if (!tieneRolCoordinador) {
            throw new IllegalArgumentException("El usuario indicado no tiene rol COORDINADOR.");
        }
        return coordinador;
    }
}
