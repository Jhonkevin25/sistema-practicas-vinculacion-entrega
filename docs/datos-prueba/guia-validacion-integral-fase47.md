# Validacion integral desde cero - version cerrada hasta Fase 47

Fecha de preparacion: 2026-07-18.

Esta guia valida el MVP actual como si la universidad iniciara su operacion:
se conserva una sola cuenta ADMIN y se reconstruyen periodos, usuarios,
entidades, cupos y expedientes. Se usa exclusivamente en el proyecto Supabase
de pruebas. Nunca se ejecuta el reinicio sobre una base de produccion.

## 1. Punto de partida seguro

1. Confirma que `git status --short` no muestre secretos ni cambios
   inesperados.
2. Crea un respaldo de Supabase antes de eliminar datos de prueba.
3. Deten el backend y el frontend. La cola de correos y los usuarios no deben
   escribir mientras se reinicia la base.
4. Ejecuta en Supabase SQL Editor:
   `database/utilidades-prueba/reinicio_mvp_solo_admin_preflight.sql`.
5. Verifica que la primera consulta muestre la cuenta ADMIN que deseas
   conservar. Si su correo no es `admin@sistema.edu.ec`, cambia el valor
   `admin_email` del script de reinicio.
6. Revisa todos los conteos del preflight y conserva la evidencia.
7. En `reinicio_mvp_solo_admin.sql`, reemplaza
   `PENDIENTE_DE_CONFIRMACION` por `REINICIAR_MVP_SOLO_ADMIN`.
8. Ejecuta el script completo. La verificacion final debe mostrar un solo
   usuario ADMIN y todos los conteos operativos en cero.
9. En Supabase Storage, vacia manualmente los objetos de prueba del bucket
   privado `documentos`. El SQL elimina metadatos academicos, no archivos del
   bucket.
10. Inicia de nuevo backend y frontend. Cierra las sesiones antiguas del
    navegador; los JWT emitidos antes del reinicio no deben reutilizarse.

El reinicio conserva `CARRERAS`, `DOCUMENTOS_REQUERIDOS`, roles, modulos,
permisos y la cuenta ADMIN. Elimina usuarios no ADMIN, expedientes, entidades,
convenios, ofertas, periodos, notificaciones, importaciones, correos y
auditoria de la prueba anterior.

## 2. Verificacion automatizada antes del flujo manual

Desde `backend/sistema-practicas`:

```powershell
.\gradlew.bat test --rerun-tasks
.\gradlew.bat build
```

Desde `frontend`:

```powershell
npm test -- --watch=false
npm run build
npm run e2e:public
```

No ejecutes pruebas backend en paralelo. Para `npm run e2e:roles`, configura
las variables `E2E_*` despues de crear y activar las nuevas cuentas.

## 3. Configuracion inicial del ADMIN

### 3.1 Periodo academico

En `Convocatorias`, crea el periodo activo `2026-2`:

| Campo | Valor de prueba |
|---|---|
| Inicio del periodo | 01/07/2026 |
| Fin del periodo | 31/12/2026 |
| Estado | ACTIVO |

Debe existir un solo periodo ACTIVO. Crea tambien `2027-1` como PLANIFICADO
para comprobar que no se activa mientras `2026-2` siga activo. No cierres
`2026-2` hasta terminar todos los expedientes.

### 3.2 Convocatorias y plazos

Para PRACTICAS y VINCULACION usa el mismo orden valido:

| Fecha | Valor de prueba |
|---|---|
| Inicio de convocatoria | 16/07/2026 |
| Limite de documentos | 18/07/2026 |
| Apertura de postulacion | 18/07/2026 |
| Cierre de convocatoria | 31/08/2026 |

Si la prueba se realiza despues del 31/08/2026, mueve todas las fechas
manteniendo el orden `inicio <= documentos <= postulacion <= cierre` y deja la
fecha actual dentro de la ventana. Configura plazos de calificacion distintos
para los parciales 1, 2 y 3.

### 3.3 Catalogos y documentos

1. Verifica que el catalogo de carreras institucionales siga disponible.
2. Revisa los requisitos conservados: hoja de vida, cedula y cartas de cada
   proceso.
3. Comprueba que ADMIN puede crear, editar, activar y desactivar requisitos.
4. Comprueba que COORDINADOR solo ve o gestiona lo permitido por el backend;
   TUTOR y ESTUDIANTE no deben entrar a configuracion administrativa.

## 4. Nuevos usuarios de gestion

Crea manualmente estas identidades ficticias con clave temporal
`Pravi2026#`. Cada una debe recibir correo y cambiar su clave en el primer
ingreso.

| Cedula | Nombre | Correo | Rol | Configuracion |
|---|---|---|---|---|
| TEST-MVP47-CP-001 | Andrea Morales | andrea.morales.mvp47@unibe.edu.ec | COORDINADOR | PRACTICAS; Ingenieria en Software |
| TEST-MVP47-CV-002 | Carlos Ponce | carlos.ponce.mvp47@unibe.edu.ec | COORDINADOR | VINCULACION; Derecho e Ingenieria en Software |
| TEST-MVP47-TP-003 | Daniela Vera | daniela.vera.mvp47@unibe.edu.ec | TUTOR | Area PRACTICAS |
| TEST-MVP47-TV-004 | Luis Herrera | luis.herrera.mvp47@unibe.edu.ec | TUTOR | Area VINCULACION |

Validaciones obligatorias:

1. ADMIN no puede desactivarse a si mismo ni eliminar al ultimo ADMIN activo.
2. Un coordinador sin alcance falla cerrado y no ve expedientes.
3. El menu del coordinador refleja proceso y carreras configuradas.
4. El tutor de PRACTICAS no ve VINCULACION y viceversa.
5. Desactivar y reactivar una cuenta conserva su historial.
6. Olvidar contrasena regresa al login con mensaje claro; cinco intentos
   incorrectos producen bloqueo temporal sin revelar si el correo existe.

## 5. Estudiantes y notas institucionales

Importa `estudiantes-validacion-fase47.csv` y
`notas-estudiantes-validacion-fase47.csv`. Ambos archivos ya usan marcadores
`MVP47` en cedulas, matriculas, correos e identificadores externos. Las tres
identidades son ficticias:

| Estudiante | Carrera | Semestre | Promedio esperado | Uso |
|---|---|---:|---:|---|
| Ana Torres | Derecho | 4 | 9.10 | Vinculacion juridica y prueba de retiro |
| Daniel Paz | Ingenieria en Software | 6 | 9.40 | Primera prioridad meritocratica |
| Emilia Cardenas | Ingenieria en Software | 6 | 8.20 | Comparacion meritocratica |

1. Importa estudiantes y comprueba tres filas correctas, cero errores y un
   correo de bienvenida por cada cuenta nueva.
2. Descarga el CSV de errores usando primero una copia con una fila invalida.
3. Reimporta el archivo correcto: no debe duplicar usuarios ni reenviar claves.
4. Importa las notas y verifica que el promedio oficial procede del semestre
   anterior. El estudiante no puede registrar su propio promedio.
5. Confirma que los tres estudiantes empiezan por VINCULACION aunque Daniel y
   Emilia esten en sexto semestre.

## 6. Empresas, convenios, ofertas y vacantes

Crea dos empresas de prueba y convenios vigentes para `2026-2`:

| Empresa | RUC | Carreras | Oferta del periodo |
|---|---|---|---:|
| Laboratorio de Software Andino MVP47 | 1799999947001 | Ingenieria en Software | 6 cupos GENERAL |
| Consultora Juridica Integral MVP47 | 1799999947002 | Derecho | 3 cupos POR_CARRERA |

En PRACTICAS publica dos vacantes activas de Ingenieria en Software, con dos
cupos cada una, 24 horas requeridas y fecha limite dentro de la convocatoria.
El total reservado no debe superar la oferta de seis cupos. Comprueba:

1. Convenio vencido, carrera no cubierta u oferta inactiva bloquean publicar.
2. Pausar una vacante la oculta al estudiante; reactivarla la devuelve.
3. Editar no duplica la vacante ni altera ocupados.
4. Duplicar copia los datos descriptivos, pero exige una nueva reserva.
5. Reprocesar una postulacion aprobada no descuenta el cupo otra vez.

## 7. Fundaciones, convenios, ofertas y proyectos

Crea dos fundaciones y sus convenios vigentes:

| Fundacion | RUC | Carreras | Oferta del periodo |
|---|---|---|---:|
| Acceso a la Justicia MVP47 | 1799999947003 | Derecho | 3 cupos POR_CARRERA |
| Comunidad Digital MVP47 | 1799999947004 | Ingenieria en Software | 4 cupos GENERAL |

Crea estos proyectos activos:

| Proyecto | Ciudad | Modalidad | Horas | Cupos |
|---|---|---|---:|---:|
| Orientacion juridica comunitaria | Quito | Presencial | 24 | 2 Derecho |
| Alfabetizacion digital comunitaria | Quito | Hibrida | 24 | 3 generales |

Comprueba la misma logica de convenio, periodo, oferta, pausa, reactivacion,
edicion y duplicado que en PRACTICAS. Los cupos del proyecto se reservan desde
la oferta de la fundacion y no se mezclan con otros periodos.

## 8. Flujo completo de VINCULACION

Realiza primero el flujo con Ana:

1. Inicia sesion como estudiante y cambia su clave temporal.
2. Sube todos los documentos obligatorios. Otro estudiante no debe poder leer
   sus archivos ni generar sus URL firmadas.
3. Postula al proyecto juridico.
4. Carlos revisa documentos y aprueba la postulacion; asigna a Luis como tutor.
5. Confirma que el proyecto pierde exactamente un cupo.
6. Retira el expediente con motivo y usa `Liberar cupo`. El cupo debe volver
   una sola vez; un segundo intento debe ser rechazado.
7. Vuelve a postular o realiza una nueva asignacion valida y completa el
   expediente.
8. Registra asistencias y tres bitacoras de 8 horas, una por parcial.
9. Luis revisa bitacoras y registra su nota; Carlos registra la suya.
10. Ana completa las tres encuestas. La nota de cada parcial solo se revela
    despues de su encuesta.
11. Cierra el expediente y revisa el acta. Ana debe avanzar a PRACTICA I.

Repite el flujo de vinculacion con Daniel y Emilia en el proyecto digital para
que ambos queden habilitados para la prueba meritocratica de PRACTICAS.

## 9. Flujo meritocratico de PRACTICAS

1. Daniel y Emilia cargan los documentos requeridos de PRACTICAS.
2. Ambos seleccionan las mismas dos vacantes, en el mismo orden. El backend
   debe exigir minimo dos, maximo tres y ninguna preferencia repetida.
3. Andrea genera la propuesta meritocratica. Daniel debe quedar primero por su
   promedio oficial 9.40 frente a 8.20.
4. Intenta enviar un promedio o score manipulado desde el navegador: el
   resultado oficial no debe cambiar.
5. Realiza un ajuste manual de la propuesta con justificacion y verifica el
   registro en auditoria.
6. Consolida las asignaciones y confirma un solo descuento por estudiante.
7. Prueba tambien una asignacion directa para un estudiante que consiguio su
   propia empresa; debe respetar convenio, carrera, periodo y cupo.
8. Retira una practica, libera el cupo con justificacion y comprueba que no se
   puede liberar dos veces.
9. Completa 24 horas, tres parciales, encuestas, documentos y cierre para al
   menos un expediente de PRACTICAS.

## 10. Seguridad y separacion por rol

Comprueba menus y tambien URLs escritas manualmente:

1. ADMIN ve administracion completa.
2. COORDINADOR PRACTICAS no consulta ni modifica VINCULACION.
3. COORDINADOR VINCULACION no consulta ni modifica PRACTICAS.
4. Cada coordinador solo ve las carreras de su alcance.
5. TUTOR solo ve sus tutorados y usa endpoints dedicados de bitacoras/notas.
6. ESTUDIANTE solo ve `/me`, sus documentos, postulaciones, encuestas y
   notificaciones.
7. Ningun recurso personal confia en un `estudianteId` o `tutorId` enviado por
   el navegador.
8. Las rutas desconocidas o no declaradas quedan denegadas por defecto.

## 11. Periodo, reportes y cierre

1. Revisa dashboard, seguimiento, filtros, paginacion, busqueda y export CSV.
2. Confirma cupos totales, reservados, ocupados y libres en ambos procesos.
3. Intenta cerrar `2026-2` con expedientes activos: debe fallar con 400.
4. Deja una postulacion pendiente cuya segunda o tercera preferencia pertenezca
   al periodo y completa/cierra todos los expedientes.
5. Cierra formalmente el periodo. La postulacion pendiente debe expirar aunque
   el periodo no este en `pref1`.
6. Un periodo CERRADO no se edita, reabre ni acepta nuevas asignaciones.
7. Los cupos sobrantes no se trasladan a `2027-1`; crea nuevas ofertas.

## 12. Navegadores, correo y Storage

Repite los recorridos principales en Chrome, Edge, Firefox y Brave, con al
menos un viewport movil. Comprueba que no haya desbordamiento horizontal,
modales cortados ni doble envio al guardar.

Mailtrap solo valida la demostracion. Antes de usuarios reales se requiere
SMTP institucional o transaccional. Un fallo SMTP debe quedar en logs/cola y
nunca devolver 500 en el flujo academico.

## 13. Criterio de aprobacion para produccion

La version es candidata a produccion solo cuando:

1. Backend y frontend mantienen todas sus pruebas verdes.
2. `npm run e2e:roles` pasa con las nuevas cuentas.
3. Esta guia manual queda marcada sin fallos, incluida la liberacion de cupos.
4. Existe respaldo y se probo una restauracion.
5. Storage privado, SMTP definitivo, dominio HTTPS, CORS y secretos de
   produccion estan configurados fuera de Git.
6. El commit desplegado es conocido y existe un artefacto anterior para
   reversion.

Hasta completar esos puntos, el sistema es un MVP funcional validado en local,
pero no una instalacion de produccion aprobada.
