package ec.edu.unibe.sistema_practicas.usuario;

import ec.edu.unibe.sistema_practicas.empresa.Empresa;
import ec.edu.unibe.sistema_practicas.tutorempresa.TutorEmpresa;
import ec.edu.unibe.sistema_practicas.tutorempresa.TutorEmpresaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class TutorAsignacionComponent {

    private final UsuarioRepository usuarioRepository;
    private final TutorEmpresaRepository tutorEmpresaRepository;

    public Usuario exigirValido(Usuario referencia, String proceso) {
        if (referencia == null || referencia.getId() == null) {
            throw new IllegalArgumentException("La asignación debe indicar un tutor académico.");
        }
        Usuario tutor = usuarioRepository.findById(referencia.getId())
                .orElseThrow(() -> new IllegalArgumentException("El tutor indicado no existe."));
        boolean tieneRol = tutor.getRoles() != null && tutor.getRoles().stream()
                .anyMatch(rol -> "TUTOR".equalsIgnoreCase(rol.getCodigo()));
        if (!tieneRol || !Boolean.TRUE.equals(tutor.getActivo())) {
            throw new IllegalArgumentException("El usuario indicado no es un tutor activo.");
        }

        String tipo = normalizar(tutor.getTutorTipo());
        String procesoNormalizado = normalizar(proceso);
        if (!Set.of("PRACTICAS", "VINCULACION").contains(procesoNormalizado)
                || !("AMBOS".equals(tipo) || procesoNormalizado.equals(tipo))) {
            throw new IllegalArgumentException(
                    "El tutor indicado no está habilitado para el proceso " + procesoNormalizado + ".");
        }
        return tutor;
    }

    public Usuario exigirValido(Usuario referencia, String proceso, Empresa empresa, String carrera) {
        Usuario tutor = exigirValido(referencia, proceso);
        if (!"PRACTICAS".equals(normalizar(proceso))) return tutor;
        if (empresa == null || empresa.getId() == null) {
            throw new IllegalArgumentException("La práctica debe indicar la empresa de la vacante.");
        }
        if (carrera == null || carrera.isBlank()) {
            throw new IllegalArgumentException("La práctica debe indicar la carrera del estudiante.");
        }
        TutorEmpresa vinculo = tutorEmpresaRepository.findByUsuarioIdAndEmpresaId(tutor.getId(), empresa.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "El tutor no está vinculado con la empresa de la vacante."));
        if (!Boolean.TRUE.equals(vinculo.getActivo())) {
            throw new IllegalArgumentException("El vínculo del tutor con la empresa está inactivo.");
        }
        boolean carreraCubierta = vinculo.getCarreras() != null && vinculo.getCarreras().stream()
                .anyMatch(item -> mismaCarrera(item.getNombre(), carrera));
        if (!carreraCubierta) {
            throw new IllegalArgumentException(
                    "El tutor no está habilitado para la carrera de la vacante en esta empresa.");
        }
        return tutor;
    }

    private String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
    }

    private boolean mismaCarrera(String izquierda, String derecha) {
        return normalizarCarrera(izquierda).equals(normalizarCarrera(derecha));
    }

    private String normalizarCarrera(String valor) {
        if (valor == null) return "";
        return Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
