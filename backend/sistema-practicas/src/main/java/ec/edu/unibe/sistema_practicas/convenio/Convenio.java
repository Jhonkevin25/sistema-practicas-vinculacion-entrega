package ec.edu.unibe.sistema_practicas.convenio;

import ec.edu.unibe.sistema_practicas.empresa.Empresa;
import ec.edu.unibe.sistema_practicas.fundacion.Fundacion;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Data
@Entity
@Table(name = "CONVENIOS")
public class Convenio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank(message = "El código del convenio es obligatorio")
    @Column(nullable = false, unique = true, length = 80)
    private String codigo;

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Empresa empresa;

    @ManyToOne
    @JoinColumn(name = "fundacion_id")
    private Fundacion fundacion;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Column(nullable = false, length = 20)
    private String estado = "VIGENTE";

    @Column(name = "url_documento", columnDefinition = "TEXT")
    private String urlDocumento;

    @Column(name = "cupos_pactados", nullable = false)
    private Integer cuposPactados = 0;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "CONVENIOS_CARRERAS", joinColumns = @JoinColumn(name = "convenio_id"))
    @Column(name = "carrera", nullable = false, length = 200)
    private Set<String> carreras = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Transient
    public boolean isVigente() {
        LocalDate hoy = LocalDate.now();
        return "VIGENTE".equalsIgnoreCase(estado)
                && fechaInicio != null
                && fechaFin != null
                && !hoy.isBefore(fechaInicio)
                && !hoy.isAfter(fechaFin);
    }

    @PrePersist
    void alCrear() {
        LocalDateTime ahora = LocalDateTime.now();
        createdAt = createdAt == null ? ahora : createdAt;
        updatedAt = ahora;
    }

    @PreUpdate
    void alActualizar() {
        updatedAt = LocalDateTime.now();
    }
}
