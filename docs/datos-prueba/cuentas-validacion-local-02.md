# Cuentas nuevas para validación local 02

Estas identidades son ficticias y se usan exclusivamente en la validación
manual del MVP. Los prefijos `MVP37` y `TEST-MVP37` permiten reconocerlas sin
confundirlas con datos institucionales reales.

## Usuarios de gestión

Crear manualmente desde `ADMIN > Usuarios`. Seleccionar fuente `Manual MVP` y
usar `Pravi2026#` como contraseña inicial de prueba. Cada cuenta debe cambiarla
en su primer ingreso.

| Cédula | Nombre | Correo | Rol | ID externo | Configuración posterior |
|---|---|---|---|---|---|
| TEST-MVP37-CP-001 | Andrea Morales | andrea.morales.mvp37@unibe.edu.ec | COORDINADOR | MVP37-COORD-PRAC-001 | Alcance PRACTICAS; Ingeniería en Software |
| TEST-MVP37-CV-002 | Carlos Ponce | carlos.ponce.mvp37@unibe.edu.ec | COORDINADOR | MVP37-COORD-VINC-002 | Alcance VINCULACION; Derecho e Ingeniería en Software |
| TEST-MVP37-TP-003 | Daniela Vera | daniela.vera.mvp37@unibe.edu.ec | TUTOR | MVP37-TUTOR-PRAC-003 | Área PRACTICAS |
| TEST-MVP37-TV-004 | Luis Herrera | luis.herrera.mvp37@unibe.edu.ec | TUTOR | MVP37-TUTOR-VINC-004 | Área VINCULACION |

Después de crear a Andrea y Carlos, abrir `Alcance` y guardar tipo y carreras.
Sin esas filas, el backend falla cerrado y el coordinador no verá estudiantes.

## Estudiantes institucionales

Importar desde `ADMIN > Importaciones > Estudiantes` el archivo
`estudiantes-validacion-local-02.csv`. El sistema debe crear tres usuarios con
rol ESTUDIANTE y enviar sus contraseñas temporales al inbox de Mailtrap:

| Nombre | Correo | Carrera | Semestre | Uso de la prueba |
|---|---|---|---:|---|
| Ana Torres | ana.torres.mvp37@est.unibe.edu.ec | Derecho | 4 | Proyecto jurídico de Vinculación |
| Daniel Paz | daniel.paz.mvp37@est.unibe.edu.ec | Ingeniería en Software | 6 | Vinculación digital y primera prioridad de Prácticas |
| Emilia Cardenas | emilia.cardenas.mvp37@est.unibe.edu.ec | Ingeniería en Software | 6 | Competencia meritocrática con menor promedio |

Después importar `notas-estudiantes-validacion-local-02.csv` desde la opción
`Notas académicas`. Los promedios oficiales esperados son 9.10, 9.40 y 8.20.

Para probar la meritocracia, Daniel y Emilia deben completar primero
Vinculación y luego seleccionar las mismas dos vacantes de Práctica I en el
mismo orden. El algoritmo debe priorizar a Daniel por su promedio oficial sin
aceptar un score enviado por el navegador.

Si se vuelve a importar el mismo archivo, no debe duplicar usuarios ni notas.
En ese caso tampoco se genera una contraseña nueva, porque la identidad ya
existe.
