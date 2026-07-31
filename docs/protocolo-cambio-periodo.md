# Protocolo operativo: cambio de periodo académico

Guía paso a paso para cerrar un periodo (ej. `2026-1`) y abrir el siguiente
(ej. `2026-2`). El sistema garantiza un solo periodo **ACTIVO** a la vez y un
cierre formal con validaciones; estos pasos son el trabajo operativo que
acompaña a esas garantías.

Roles involucrados: **ADMIN** ejecuta los pasos 2–5; **COORDINADOR** y
**TUTOR** completan el paso 1 y verifican el 6.

---

## 1. Pre-cierre (últimas semanas del periodo) — Coordinación y Tutores

- [ ] Tutores: aprobar o rechazar todas las **bitácoras pendientes**
      (Prácticas/Vinculación → Revisar bitácoras, filtro "Pendientes").
- [ ] Tutores y Coordinación: registrar las **notas de los 3 parciales** de
      cada expediente.
- [ ] Coordinación: **cerrar cada expediente** que terminó
      (botón *Cerrar* → genera acta) o aplicar *Finalización excepcional* con
      su justificación para retiros/abandonos.
- [ ] Verificación: el sistema **no permite cerrar el periodo** si queda algún
      expediente en estado `pendiente` o `en_curso` — esa es la señal de que
      falta trabajo de este paso.

## 2. Cierre formal del periodo — ADMIN

En **Convocatorias → Periodos académicos**, acción **Cerrar** sobre el periodo
vigente. El sistema automáticamente:

- expira las postulaciones que quedaron pendientes,
- bloquea nuevas asignaciones en ese periodo,
- deja el periodo como `CERRADO` (histórico, consultable en los selectores de
  periodo de tutores y estudiantes).

## 3. Crear y activar el periodo nuevo — ADMIN

- [ ] Crear el periodo (código `YYYY-N`, fecha de inicio y fin) — nace como
      `PLANIFICADO`.
- [ ] **Activarlo**. El sistema rechaza la activación si otro periodo sigue
      activo (primero el paso 2).
- [ ] A partir de aquí, todo lo nuevo (postulaciones, vacantes, expedientes,
      cupos) se sella con el periodo nuevo automáticamente.

## 4. Actualizar la base de estudiantes — ADMIN

El semestre y periodo de cada estudiante **no se promueven solos** (la fuente
de verdad es el sistema institucional):

- [ ] **Importaciones → Estudiantes (CSV)** con el corte del nuevo periodo:
      actualiza semestre y periodo de cada estudiante.
- [ ] **Importaciones → Notas académicas (CSV)**: carga las notas del semestre
      anterior para el cálculo meritocrático.
- [ ] Coordinación: **verificar** las notas importadas
      (Estudiantes → Notas académicas para meritocracia) — sin nota verificada
      no corre la meritocracia del estudiante.
- Alternativa puntual: coordinación puede ajustar semestre/periodo de un
  estudiante concreto desde su formulario reducido.

## 5. Configurar el periodo nuevo — ADMIN

La configuración es **por periodo** y no se copia sola del anterior:

- [ ] **Convocatoria**: ventana de postulación y fecha límite de documentos
      (por proceso: Prácticas y Vinculación).
- [ ] **Fechas límite de calificación** de los parciales 1, 2 y 3.
- [ ] **Ofertas de cupos** por empresa y por fundación (capacidad del periodo).
- [ ] **Vacantes** de prácticas del periodo.

## 6. Verificación de arranque — todos los roles

- [ ] Los usuarios conectados deben **refrescar la sesión** (o volver a entrar)
      para ver el periodo nuevo.
- [ ] Tutor: sus pestañas arrancan en el periodo activo; el ciclo anterior
      queda en el selector "Periodo" como historial.
- [ ] Estudiante: ve la convocatoria nueva; su historial queda en
      **Mi Expediente** por periodo.
- [ ] Coordinación: revisa que los estudiantes de su alcance tengan semestre y
      periodo correctos tras la importación.

---

## Resumen de una línea

**Resolver y cerrar todo → Cerrar periodo → Crear y activar el nuevo →
Importar estudiantes y notas → Configurar convocatoria, fechas y cupos →
Refrescar sesiones.**

> Mejora futura sugerida (fuera del MVP): botón "duplicar configuración del
> periodo anterior" para el paso 5.
