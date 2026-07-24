package ec.edu.unibe.sistema_practicas.ofertacupo;

import ec.edu.unibe.sistema_practicas.empresa.Empresa;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "OFERTAS_CUPOS_EMPRESA")
public class OfertaCuposEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

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
    private List<OfertaCuposEmpresaCarrera> carreras = new ArrayList<>();

    @Transient
    private Integer cuposReservados = 0;

    @Transient
    private Integer cuposOcupados = 0;

    @Transient
    private Integer cuposDisponibles = 0;
}
