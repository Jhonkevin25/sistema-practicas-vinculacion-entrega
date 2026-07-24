package ec.edu.unibe.sistema_practicas.seguimiento;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Herramienta operativa para disparar manualmente la revision de atrasos sin
// esperar al cron diario (util para pruebas). Restringido a ADMIN.
@RestController
@RequestMapping("/api/seguimiento")
@RequiredArgsConstructor
public class AlertaAtrasoController {

    private final AlertaAtrasoScheduler alertaAtrasoScheduler;

    @PostMapping("/alertas-atraso/ejecutar")
    public ResponseEntity<Void> ejecutarManualmente() {
        alertaAtrasoScheduler.revisarAtrasos();
        return ResponseEntity.ok().build();
    }
}
