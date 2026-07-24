# Datos para prueba integral por roles

Este conjunto es exclusivamente de prueba. Permite comprobar el alta realizada
por el ADMIN, la separacion entre PRACTICAS y VINCULACION, el alcance por
carrera, la importacion institucional y el flujo academico de dos estudiantes.

## 1. Catalogo institucional

Vuelve a ejecutar `database/fase27_catalogo_carreras.sql` en el SQL Editor de
Supabase. Es idempotente: agrega las carreras faltantes sin duplicar ni borrar
las existentes.

El catalogo base queda con:

- Derecho
- Enfermeria
- Estetica Integral
- Fisioterapia
- Gastronomia
- Ingenieria en Software
- Medicina
- Multimedia y Produccion Audiovisual
- Nutricion y Dietetica
- Odontologia
- Psicologia
- Psicologia Clinica

El ADMIN puede agregar o desactivar otras carreras desde Convocatorias >
Catalogo de carreras.

## 2. Usuarios de gestion

Crealos desde ADMIN > Usuarios. Usa `Pravi2026#` solo como clave temporal de
prueba. Cada cuenta debe cambiarla al ingresar por primera vez.

| Cedula | Nombre | Correo | Rol | Configuracion |
|---|---|---|---|---|
| TEST-COOR-PRAC-01 | Camila Andrade | coordinacion.practicas.prueba@unibe.edu.ec | COORDINADOR | Alcance PRACTICAS; Ingenieria en Software |
| TEST-COOR-VINC-01 | Diego Cevallos | coordinacion.vinculacion.prueba@unibe.edu.ec | COORDINADOR | Alcance VINCULACION; Derecho e Ingenieria en Software |
| TEST-TUTOR-PRAC-01 | Sofia Paredes | tutoria.practicas.prueba@unibe.edu.ec | TUTOR | Area PRACTICAS |
| TEST-TUTOR-VINC-01 | Martin Salazar | tutoria.vinculacion.prueba@unibe.edu.ec | TUTOR | Area VINCULACION |

Despues de crear cada coordinador, pulsa Alcance y guarda tipo y carreras. Si
el alcance no tiene carreras, el backend falla cerrado y el coordinador no ve
estudiantes.

## 3. Estudiantes por CSV

En ADMIN > Importacion institucional:

1. Selecciona Estudiantes.
2. Importa `estudiantes-flujo-completo.csv`.
3. Verifica 2 filas correctas y 0 errores.
4. Revisa Mailtrap: cada estudiante nuevo recibe una clave temporal distinta.
5. Selecciona Notas academicas.
6. Importa `notas-estudiantes-flujo-completo.csv`.
7. Verifica los promedios oficiales 9.20 y 8.70 del semestre anterior.

| Estudiante | Carrera | Semestre actual | Inicio esperado |
|---|---|---:|---|
| Valentina Mena | Derecho | 4 | Vinculacion |
| Mateo Ruiz | Ingenieria en Software | 6 | Vinculacion si no tiene un expediente anterior cerrado |

La etapa no se fuerza desde el CSV. Un estudiante nuevo debe completar
Vinculacion antes de Practica I y luego Practica II, aunque sea importado en un
semestre superior. Esto permite comprobar la secuencia academica real.

## 4. Empresas y convenios

Crealas desde ADMIN > Empresas y luego usa el boton Convenios.

### Empresa 1

| Campo | Valor |
|---|---|
| RUC | 1799999901001 |
| Nombre | Clinica Integral Horizonte (Prueba) |
| Direccion | Av. Salud 100, Quito |
| Cupos disponibles | 4 |
| Codigo convenio | CONV-EMP-SALUD-2026 |
| Vigencia | 2026-01-01 a 2027-12-31 |
| Estado | VIGENTE |
| Cupos pactados | 4 |
| Carreras | Enfermeria, Fisioterapia, Medicina, Nutricion y Dietetica, Odontologia |

### Empresa 2

| Campo | Valor |
|---|---|
| RUC | 1799999902001 |
| Nombre | Estudio Multimedia Andino (Prueba) |
| Direccion | Av. Tecnologia 200, Quito |
| Cupos disponibles | 4 |
| Codigo convenio | CONV-EMP-DIGITAL-2026 |
| Vigencia | 2026-01-01 a 2027-12-31 |
| Estado | VIGENTE |
| Cupos pactados | 4 |
| Carreras | Ingenieria en Software, Multimedia y Produccion Audiovisual |

Crea al menos una vacante activa para la segunda empresa, carrera Ingenieria
en Software, periodo 2026-2 y 2 cupos. Mateo podra usarla cuando complete su
etapa previa de Vinculacion.

## 5. Fundaciones, convenios y proyectos

Crealas desde ADMIN > Fundaciones & Proyectos. Registra tambien un convenio
vigente y un proyecto activo por fundacion.

### Fundacion 1

| Campo | Valor |
|---|---|
| RUC | 1799999903001 |
| Nombre | Fundacion Acceso a la Justicia (Prueba) |
| Area | Derechos y orientacion comunitaria |
| Mision | Facilitar orientacion juridica y acompanamiento comunitario. |
| Codigo convenio | CONV-FUND-JUSTICIA-2026 |
| Vigencia | 2026-01-01 a 2027-12-31 |
| Cupos pactados | 4 |
| Carreras | Derecho, Psicologia, Psicologia Clinica |
| Proyecto | Orientacion juridica comunitaria |
| Periodo y cupos | 2026-2; 2 cupos |

### Fundacion 2

| Campo | Valor |
|---|---|
| RUC | 1799999904001 |
| Nombre | Fundacion Comunidad Digital (Prueba) |
| Area | Educacion y tecnologia comunitaria |
| Mision | Reducir brechas digitales mediante formacion comunitaria. |
| Codigo convenio | CONV-FUND-DIGITAL-2026 |
| Vigencia | 2026-01-01 a 2027-12-31 |
| Cupos pactados | 4 |
| Carreras | Ingenieria en Software, Multimedia y Produccion Audiovisual |
| Proyecto | Alfabetizacion digital comunitaria |
| Periodo y cupos | 2026-2; 2 cupos |

## 6. Orden de prueba

1. ADMIN configura fechas 2026-2 y documentos requeridos para ambos procesos.
2. ADMIN crea usuarios, alcances, empresas, fundaciones, convenios y ofertas.
3. ADMIN importa estudiantes y notas.
4. Valentina se postula al proyecto juridico.
5. Mateo se postula al proyecto digital.
6. COORDINADOR de Vinculacion revisa documentos y resuelve postulaciones.
7. TUTOR de Vinculacion revisa bitacoras y registra sus notas.
8. COORDINADOR completa sus notas y cierra al menos un expediente.
9. Confirma que el estudiante cerrado avanza a Practica I.
10. Prueba la vacante digital con el estudiante que ya completo Vinculacion.
11. Comprueba cupos, notificaciones, correos, encuestas y reportes.

No uses estas identidades ni la clave temporal en produccion. Los prefijos
`TEST-` y `MVP-` permiten reconocer estos registros de prueba.
