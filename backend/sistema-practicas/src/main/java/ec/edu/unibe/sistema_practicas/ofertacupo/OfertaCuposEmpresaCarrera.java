package ec.edu.unibe.sistema_practicas.ofertacupo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ec.edu.unibe.sistema_practicas.carrera.Carrera;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@Table(name = "OFERTAS_CUPOS_EMPRESA_CARRERAS")
public class OfertaCuposEmpresaCarrera {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne
    @JoinColumn(name = "oferta_id", nullable = false)
    private OfertaCuposEmpresa oferta;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "carrera_id", nullable = false)
    private Carrera carrera;

    @NotNull
    @Column(nullable = false)
    private Integer cupos;

    @Transient
    private Integer cuposReservados = 0;

    @Transient
    private Integer cuposOcupados = 0;

    @Transient
    private Integer cuposDisponibles = 0;
}
