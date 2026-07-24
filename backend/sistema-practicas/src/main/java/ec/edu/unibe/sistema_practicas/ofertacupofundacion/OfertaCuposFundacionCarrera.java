package ec.edu.unibe.sistema_practicas.ofertacupofundacion;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ec.edu.unibe.sistema_practicas.carrera.Carrera;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@Table(name = "OFERTAS_CUPOS_FUNDACION_CARRERAS")
public class OfertaCuposFundacionCarrera {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne
    @JoinColumn(name = "oferta_id", nullable = false)
    private OfertaCuposFundacion oferta;

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
