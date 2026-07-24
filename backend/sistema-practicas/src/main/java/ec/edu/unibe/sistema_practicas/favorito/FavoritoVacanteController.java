package ec.edu.unibe.sistema_practicas.favorito;

import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.estudiante.EstudianteRepository;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import ec.edu.unibe.sistema_practicas.vacante.VacantePracticaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/favoritos")
@RequiredArgsConstructor
public class FavoritoVacanteController {

    private final FavoritoVacanteRepository favoritoRepository;
    private final EstudianteRepository estudianteRepository;
    private final VacantePracticaRepository vacanteRepository;

    // Los favoritos son personales: siempre se resuelven desde el JWT,
    // nunca desde un id enviado por el cliente
    private Optional<Estudiante> estudianteActual(Usuario usuario) {
        if (usuario == null) return Optional.empty();
        return estudianteRepository.findByUsuarioEmail(usuario.getEmail());
    }

    private List<Integer> idsDeVacantes(Integer estudianteId) {
        return favoritoRepository.findByEstudianteId(estudianteId).stream()
                .map(f -> f.getVacante().getId())
                .toList();
    }

    // Ids de vacantes favoritas del estudiante autenticado; sin expediente
    // de estudiante la lista es vacia (no es un error)
    @GetMapping("/me")
    public List<Integer> getMisFavoritos(@AuthenticationPrincipal Usuario usuario) {
        return estudianteActual(usuario)
                .map(est -> idsDeVacantes(est.getId()))
                .orElse(List.of());
    }

    // Marca o desmarca una vacante como favorita y devuelve la lista actualizada
    @PostMapping("/toggle")
    public List<Integer> toggle(@RequestParam Integer vacanteId,
                                @AuthenticationPrincipal Usuario usuario) {
        Estudiante estudiante = estudianteActual(usuario)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tu usuario no tiene un expediente de estudiante asociado."));

        favoritoRepository.findByEstudianteIdAndVacanteId(estudiante.getId(), vacanteId)
                .ifPresentOrElse(favoritoRepository::delete, () -> {
                    if (!vacanteRepository.existsById(vacanteId)) {
                        throw new IllegalArgumentException("La vacante indicada no existe.");
                    }
                    FavoritoVacante favorito = new FavoritoVacante();
                    favorito.setEstudiante(estudiante);
                    favorito.setVacante(vacanteRepository.getReferenceById(vacanteId));
                    favoritoRepository.save(favorito);
                });

        return idsDeVacantes(estudiante.getId());
    }
}
