package ec.edu.unibe.sistema_practicas.vacante;

import ec.edu.unibe.sistema_practicas.empresa.Empresa;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "VACANTES_PRACTICAS")
public class VacantePractica {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank
    private String nombre;

    @ManyToOne
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    private Integer cupos;
    private Integer horas;
    private String descripcion;
    
    @NotBlank
    private String carrera;
    
    @NotBlank
    @Column(name = "modalidad_academica")
    private String modalidadAcademica;
    
    @NotBlank
    private String area;
    
    @NotBlank
    private String ciudad;
    
    @NotBlank
    @Column(name = "tipo_empresa")
    private String tipoEmpresa;
    
    @NotBlank
    @Column(name = "modalidad_trabajo")
    private String modalidadTrabajo;
    
    @Column(name = "fecha_limite")
    private LocalDate fechaLimite;
    
    @Column(name = "alta_demanda")
    private Boolean altaDemanda = false;
    
    private String requisitos;
    
    @Column(name = "perfil_requerido")
    private String perfilRequerido;

    @NotBlank
    @Column(name = "periodo_academico", length = 20)
    private String periodoAcademico;

    // Fase 42: pausa/reactivación sin devolver los cupos a la empresa
    @Column(nullable = false)
    private Boolean activa = true;
}
