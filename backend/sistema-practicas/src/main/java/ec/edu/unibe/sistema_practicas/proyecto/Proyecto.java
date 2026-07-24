package ec.edu.unibe.sistema_practicas.proyecto;

import ec.edu.unibe.sistema_practicas.fundacion.Fundacion;
import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "proyectos")
public class Proyecto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "fundacion_id", nullable = false)
    private Fundacion fundacion;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "cupos_totales", nullable = false)
    private Integer cuposTotales = 0;

    @Column(name = "cupos_disponibles", nullable = false)
    private Integer cuposDisponibles = 0;

    @Column(name = "horas_requeridas", nullable = false)
    private Integer horasRequeridas = 160;

    @Column(nullable = false, length = 150)
    private String ciudad;

    @Column(nullable = false, length = 20)
    private String modalidad = "NO_ESPECIFICADA";

    private Boolean estado = true;

    @Column(nullable = false, length = 20)
    private String periodo;

    @OneToMany(mappedBy = "proyecto", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<ProyectoCarrera> carreras = new ArrayList<>();
}
