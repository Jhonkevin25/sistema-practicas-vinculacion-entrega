package ec.edu.unibe.sistema_practicas.tutorempresa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TutorEmpresaRepository extends JpaRepository<TutorEmpresa, Integer> {
    List<TutorEmpresa> findByEmpresaIdOrderByNombreAsc(Integer empresaId);
    List<TutorEmpresa> findByEmpresaIdAndActivoTrueOrderByNombreAsc(Integer empresaId);
    Optional<TutorEmpresa> findByUsuarioIdAndEmpresaId(Integer usuarioId, Integer empresaId);
}
