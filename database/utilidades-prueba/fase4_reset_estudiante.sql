-- ============================================================
-- RESET DEL EXPEDIENTE DEL ESTUDIANTE DE PRUEBA
-- Ejecutar en Supabase SQL Editor. Idempotente / repetible.
-- ============================================================
-- Cambio:  borra TODO el expediente academico del estudiante de prueba
--          EST-2026-0001 (practicas con sus evaluaciones, postulaciones,
--          bitacoras, asistencias y vinculaciones) y lo deja nuevamente
--          en etapa "Práctica I" (vinculacion completada 160/160 de un
--          periodo anterior), listo para recorrer el proceso desde cero:
--          documentos -> postulacion -> meritocracia -> consolidar ->
--          asignar tutor -> bitacoras -> calificaciones.
-- Motivo:  poder repetir el flujo MVP completo sin crear otro usuario.
-- Impacto: POSTULACIONES_MERITOCRATICAS, BITACORAS, ASISTENCIAS,
--          PRACTICAS (+cascada evaluaciones/documentos), VINCULACION
--          (+cascada), FUNDACIONES/PROYECTOS solo si faltan.
-- NOTA:    ejecutalo cada vez que quieras "reiniciar" al estudiante.

BEGIN;

INSERT INTO PERIODOS_ACADEMICOS (codigo, fecha_inicio, fecha_fin, estado)
VALUES ('2026-1', '2026-01-01', '2026-06-30', 'CERRADO')
ON CONFLICT (codigo) DO NOTHING;

CREATE TEMP TABLE _est ON COMMIT DROP AS
SELECT id FROM ESTUDIANTES WHERE matricula = 'EST-2026-0001';

-- 1. Borrar el expediente completo
DELETE FROM POSTULACIONES_MERITOCRATICAS WHERE estudiante_id IN (SELECT id FROM _est);
DELETE FROM BITACORAS                    WHERE estudiante_id IN (SELECT id FROM _est);
DELETE FROM ASISTENCIAS                  WHERE estudiante_id IN (SELECT id FROM _est);
DELETE FROM PRACTICAS                    WHERE estudiante_id IN (SELECT id FROM _est);
DELETE FROM VINCULACION                  WHERE estudiante_id IN (SELECT id FROM _est);
DELETE FROM DOCS_ESTUDIANTE              WHERE estudiante_id IN (SELECT id FROM _est);
DELETE FROM FAVORITOS_VACANTES           WHERE estudiante_id IN (SELECT id FROM _est);

-- 2. Fundacion y proyecto de prueba (crear solo si no existen)
INSERT INTO FUNDACIONES (ruc, nombre, mision, area_intervencion, activa, tiene_convenio)
SELECT '1799999999001', 'Fundación de Prueba UNIBE', 'Datos de prueba del sistema', 'Educación', TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM FUNDACIONES WHERE ruc = '1799999999001');

INSERT INTO PROYECTOS
    (fundacion_id, nombre, descripcion, cupos_totales, cupos_disponibles,
     horas_requeridas, ciudad, modalidad, estado, periodo)
SELECT f.id, 'Proyecto de Prueba - Alfabetización Digital',
       'Proyecto usado para validar el flujo académico.', 5, 5,
       160, 'Quito', 'PRESENCIAL', TRUE, '2026-1'
FROM FUNDACIONES f
WHERE f.ruc = '1799999999001'
  AND NOT EXISTS (
      SELECT 1 FROM PROYECTOS WHERE nombre = 'Proyecto de Prueba - Alfabetización Digital'
  );

INSERT INTO OFERTAS_CUPOS_FUNDACION
    (fundacion_id, periodo_academico, distribucion, cupos_totales, activo, observacion)
SELECT f.id, '2026-1', 'GENERAL', 5, FALSE, 'Oferta histórica del fixture de prueba.'
FROM FUNDACIONES f
WHERE f.ruc = '1799999999001'
ON CONFLICT (fundacion_id, periodo_academico) DO NOTHING;

INSERT INTO PROYECTOS_CARRERAS (proyecto_id, carrera_id)
SELECT p.id, c.id
FROM PROYECTOS p
JOIN CARRERAS c ON c.nombre = 'Ingeniería en Software'
WHERE p.nombre = 'Proyecto de Prueba - Alfabetización Digital'
ON CONFLICT (proyecto_id, carrera_id) DO NOTHING;

-- 3. Vinculacion completada (periodo anterior) => etapa actual: Práctica I
INSERT INTO VINCULACION
    (estudiante_id, fundacion_id, proyecto_id, estado,
     horas_requeridas, horas_completadas, fecha_inicio, fecha_fin, periodo_academico)
SELECT e.id, f.id, p.id, 'completado', 160, 160,
       '2026-01-15', '2026-05-30', '2026-1'
FROM _est e
JOIN FUNDACIONES f ON f.ruc = '1799999999001'
JOIN PROYECTOS p ON p.nombre = 'Proyecto de Prueba - Alfabetización Digital';

COMMIT;

-- ============================================================
-- Verificacion: expediente limpio, solo la vinculacion completada
-- ============================================================
SELECT 'practicas' AS tabla, COUNT(*) AS filas FROM PRACTICAS
WHERE estudiante_id = (SELECT id FROM ESTUDIANTES WHERE matricula = 'EST-2026-0001')
UNION ALL
SELECT 'postulaciones', COUNT(*) FROM POSTULACIONES_MERITOCRATICAS
WHERE estudiante_id = (SELECT id FROM ESTUDIANTES WHERE matricula = 'EST-2026-0001')
UNION ALL
SELECT 'bitacoras', COUNT(*) FROM BITACORAS
WHERE estudiante_id = (SELECT id FROM ESTUDIANTES WHERE matricula = 'EST-2026-0001')
UNION ALL
SELECT 'vinculaciones (debe ser 1, completada)', COUNT(*) FROM VINCULACION
WHERE estudiante_id = (SELECT id FROM ESTUDIANTES WHERE matricula = 'EST-2026-0001');
