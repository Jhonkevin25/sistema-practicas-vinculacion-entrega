package ec.edu.unibe.sistema_practicas.coordinador;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CoordinadorCarreraRepository extends JpaRepository<CoordinadorCarrera, Integer> {
    List<CoordinadorCarrera> findByUsuarioId(Integer usuarioId);
    List<CoordinadorCarrera> findByCarreraIgnoreCase(String carrera);
    void deleteByUsuarioId(Integer usuarioId);
}
