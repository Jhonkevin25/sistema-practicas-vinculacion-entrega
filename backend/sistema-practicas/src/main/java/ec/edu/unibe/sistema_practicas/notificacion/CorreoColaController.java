package ec.edu.unibe.sistema_practicas.notificacion;

import ec.edu.unibe.sistema_practicas.paginacion.PaginaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/correos")
@RequiredArgsConstructor
public class CorreoColaController {

    private final CorreoColaRepository correoColaRepository;

    @GetMapping("/paginado")
    public PaginaResponse<CorreoCola> obtenerPaginado(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String destinatario) {
        
        if (size > 100) size = 100;
        Pageable pageable = PageRequest.of(page, size, Sort.by("fechaCreacion").descending());
        
        String estadoFiltro = estado != null && estado.isBlank() ? null : estado;
        String destinatarioFiltro = destinatario != null && destinatario.isBlank() ? null : destinatario;
        
        Page<CorreoCola> pagina = correoColaRepository.findByFiltros(estadoFiltro, destinatarioFiltro, pageable);
        
        return new PaginaResponse<>(
                pagina.getContent(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages()
        );
    }

    @PostMapping("/{id}/reintentar")
    @Transactional
    public CorreoCola reintentar(@PathVariable Long id) {
        CorreoCola correo = correoColaRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el correo en la cola."));

        if (!"FALLIDO".equals(correo.getEstado())) {
            throw new IllegalArgumentException(
                    "Solo se pueden reintentar correos en estado FALLIDO.");
        }
        
        correo.setEstado("PENDIENTE");
        correo.setIntentos(0);
        correo.setUltimoError(null);
        correo.setFechaActualizacion(LocalDateTime.now());
        
        return correoColaRepository.save(correo);
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id) {
        if (!correoColaRepository.existsById(id)) {
            throw new IllegalArgumentException("No se encontró el correo en la cola.");
        }
        correoColaRepository.deleteById(id);
    }
}
