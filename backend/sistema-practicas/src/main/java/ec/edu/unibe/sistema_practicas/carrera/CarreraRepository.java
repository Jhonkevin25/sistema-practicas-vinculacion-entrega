package ec.edu.unibe.sistema_practicas.carrera;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CarreraRepository extends JpaRepository<Carrera, Integer> {
    List<Carrera> findAllByOrderByNombreAsc();
    Optional<Carrera> findByNombreIgnoreCase(String nombre);
}
