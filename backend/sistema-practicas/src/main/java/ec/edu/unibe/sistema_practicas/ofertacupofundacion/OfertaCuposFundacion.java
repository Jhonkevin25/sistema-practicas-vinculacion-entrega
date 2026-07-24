package ec.edu.unibe.sistema_practicas.ofertacupofundacion;

import ec.edu.unibe.sistema_practicas.fundacion.Fundacion;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "OFERTAS_CUPOS_FUNDACION")
public class OfertaCuposFundacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "fundacion_id", nullable = false)
    private Fundacion fundacion;

    @NotBlank
    @Column(name = "periodo_academico", nullable = false, length = 20)
    private String periodoAcademico;

    @NotBlank
    @Column(nullable = false, length = 20)
    private String distribucion = "GENERAL";

    @NotNull
    @Column(name = "cupos_totales", nullable = false)
    private Integer cuposTotales = 0;

    @NotNull
    @Column(nullable = false)
    private Boolean activo = true;

    @Column(columnDefinition = "TEXT")
    private String observacion;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "oferta", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<OfertaCuposFundacionCarrera> carreras = new ArrayList<>();

    @Transient
    private Integer cuposReservados = 0;

    @Transient
    private Integer cuposOcupados = 0;

    @Transient
    private Integer cuposDisponibles = 0;
}
