package ec.edu.unibe.sistema_practicas.tutorfundacion;

import ec.edu.unibe.sistema_practicas.carrera.Carrera;
import ec.edu.unibe.sistema_practicas.fundacion.Fundacion;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Data
@Entity
@Table(name = "TUTORES_FUNDACION")
public class TutorFundacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "fundacion_id", nullable = false)
    private Fundacion fundacion;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(length = 100)
    private String cargo;

    @Column(nullable = false)
    private Boolean activo = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "TUTORES_FUNDACION_CARRERAS",
            joinColumns = @JoinColumn(name = "tutor_fundacion_id"),
            inverseJoinColumns = @JoinColumn(name = "carrera_id")
    )
    @OrderBy("nombre ASC")
    @BatchSize(size = 20)
    private Set<Carrera> carreras = new LinkedHashSet<>();
}
