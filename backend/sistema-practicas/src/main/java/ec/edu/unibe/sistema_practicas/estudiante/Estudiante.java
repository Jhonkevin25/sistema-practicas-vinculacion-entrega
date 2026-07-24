package ec.edu.unibe.sistema_practicas.estudiante;

import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "estudiantes")
public class Estudiante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @Column(nullable = false, unique = true)
    private String matricula;

    @Column(nullable = false)
    private String carrera;

    private Integer semestre;

    @Column(name = "periodo_academico")
    private String periodoAcademico;
}
