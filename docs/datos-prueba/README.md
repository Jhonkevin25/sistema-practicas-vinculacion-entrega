# Datos de prueba del flujo MVP

Estos archivos permiten probar la importacion institucional sin crear varios
estudiantes. La unica cuenta estudiantil incluida corresponde a:

- Nombre: Jhon Chipugsi
- Correo institucional: `jchipugsim@est.unibe.edu.ec`
- Identificador externo: `MVP-JCHIPUGSIM-2026`
- Cedula de prueba: `TEST-JC-2026-01`
- Matricula de prueba: `MVP-2026-JC-001`

La cedula, matricula, periodos y promedio son datos de prueba. Los
identificadores comienzan con `MVP-` o `TEST-` para poder localizarlos y
eliminarlos de forma dirigida al terminar la validacion.

## Orden de importacion

1. Iniciar sesion como ADMIN.
2. Importar `estudiante-mvp.csv` desde la carga institucional de estudiantes.
3. Comprobar que Jhon quedo en sexto semestre, activo y en la carrera
   `Ingeniería en Software`.
4. Importar `notas-estudiante-mvp.csv` desde la carga institucional de notas.
5. Comprobar que se registro el promedio `8.75` para quinto semestre y periodo
   `2026-1`.

El backend genera una contrasena temporal cuando crea una cuenta nueva. Con
Mailtrap configurado, el mensaje de bienvenida y la contrasena deben revisarse
en el inbox de Mailtrap. En el primer ingreso, el estudiante debe cambiarla.

Las importaciones son repetibles: si la cuenta ya existe con el mismo correo o
identificador externo, el sistema debe actualizar o enlazar el registro en vez
de duplicarlo. Si la cuenta ya existia, no se genera otra contrasena temporal.

Los usuarios de ADMIN, COORDINADOR y TUTOR no deben agregarse en este CSV,
porque este importador asigna exclusivamente el rol ESTUDIANTE. Para probar los
otros roles se usan las cuentas de prueba creadas por los scripts dedicados del
proyecto.

## Prueba integral con dos estudiantes y roles separados

Para validar el alta manual del ADMIN y los procesos completos de Practicas y
Vinculacion, usa:

- `estudiantes-flujo-completo.csv`
- `notas-estudiantes-flujo-completo.csv`
- `guia-flujo-completo.md`

La guia incluye dos coordinadores, dos tutores, dos empresas, dos fundaciones,
convenios, proyectos y el orden recomendado de validacion.

## Validacion integral de la version cerrada hasta Fase 47

Para reiniciar el entorno de pruebas conservando solo al ADMIN y repetir los
flujos actuales de periodos, ofertas, cupos, retiros y cierre, usar
`guia-validacion-integral-fase47.md`. Antes de cualquier limpieza exige el
preflight y el reinicio protegido de `database/utilidades-prueba/`.
