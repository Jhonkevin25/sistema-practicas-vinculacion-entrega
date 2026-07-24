package ec.edu.unibe.sistema_practicas.bitacora;

import ec.edu.unibe.sistema_practicas.practica.Practica;
import ec.edu.unibe.sistema_practicas.vinculacion.Vinculacion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HorasExpedienteComponent {

    private final BitacoraRepository bitacoraRepository;

    public int aplicarHorasAprobadas(Practica practica) {
        validarHorasRequeridas(practica.getHorasRequeridas());
        int horas = practica.getId() == null
                ? 0
                : bitacoraRepository.sumHorasAprobadasByPracticaId(practica.getId());
        int computables = horasComputables(horas, practica.getHorasRequeridas());
        practica.setHorasCompletadas(computables);
        return computables;
    }

    public int aplicarHorasAprobadas(Vinculacion vinculacion) {
        validarHorasRequeridas(vinculacion.getHorasRequeridas());
        int horas = vinculacion.getId() == null
                ? 0
                : bitacoraRepository.sumHorasAprobadasByVinculacionId(vinculacion.getId());
        int computables = horasComputables(horas, vinculacion.getHorasRequeridas());
        vinculacion.setHorasCompletadas(computables);
        return computables;
    }

    private void validarHorasRequeridas(Integer horasRequeridas) {
        if (horasRequeridas == null || horasRequeridas <= 0) {
            throw new IllegalArgumentException("Las horas requeridas deben ser mayores a cero.");
        }
    }

    private int horasComputables(int horasAprobadas, int horasRequeridas) {
        return Math.min(Math.max(horasAprobadas, 0), horasRequeridas);
    }
}
