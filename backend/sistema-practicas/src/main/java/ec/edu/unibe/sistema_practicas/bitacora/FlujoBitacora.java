package ec.edu.unibe.sistema_practicas.bitacora;

import java.util.List;
import java.util.Objects;

public final class FlujoBitacora {

    private FlujoBitacora() {}

    public static boolean tieneAbiertas(List<Bitacora> bitacoras) {
        return tieneAbiertas(bitacoras, null);
    }

    public static boolean tieneAbiertas(List<Bitacora> bitacoras, Integer parcial) {
        return bitacoras.stream()
                .filter(bitacora -> parcial == null || Objects.equals(parcial, bitacora.getParcial()))
                .anyMatch(bitacora -> "pendiente".equalsIgnoreCase(bitacora.getEstado())
                        || esCorreccionSinResolver(bitacora, bitacoras));
    }

    public static boolean tieneCorreccionSinResolver(List<Bitacora> bitacoras, Integer parcial) {
        return bitacoras.stream()
                .filter(bitacora -> Objects.equals(parcial, bitacora.getParcial()))
                .anyMatch(bitacora -> esCorreccionSinResolver(bitacora, bitacoras));
    }

    public static boolean esCorreccionSinResolver(Bitacora bitacora, List<Bitacora> bitacoras) {
        if (!"requiere_correccion".equalsIgnoreCase(bitacora.getEstado())) return false;
        return bitacoras.stream().noneMatch(otra ->
                "aprobada".equalsIgnoreCase(otra.getEstado())
                        && Objects.equals(otra.getParcial(), bitacora.getParcial())
                        && esPosterior(otra, bitacora));
    }

    private static boolean esPosterior(Bitacora otra, Bitacora base) {
        return otra.getId() != null && base.getId() != null && otra.getId() > base.getId();
    }
}
