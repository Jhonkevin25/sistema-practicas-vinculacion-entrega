-- ============================================================
-- OPCIONAL: AVANZAR AL ESTUDIANTE DE PRUEBA A ETAPA "PRÁCTICA I"
-- Ejecutar en Supabase SQL Editor. Idempotente.
-- ============================================================
-- Cambio:  registra una Vinculación COMPLETADA (160/160 h) para el
--          estudiante de prueba EST-2026-0001, creando una fundación y
--          un proyecto de prueba si no existen.
-- Motivo:  la etapa académica ahora se deriva del expediente real:
--          sin vinculación completada, el estudiante está en etapa
--          "Vinculación" y NO ve vacantes de Práctica I/II (flujo de
--          negocio correcto). Este script lo avanza para poder probar
--          el flujo meritocrático de prácticas.
-- Impacto: FUNDACIONES, PROYECTOS, VINCULACION.

INSERT INTO PERIODOS_ACADEMICOS (codigo, fecha_inicio, fecha_fin, estado)
VALUES ('2026-1', '2026-01-01', '2026-06-30', 'CERRADO')
ON CONFLICT (codigo) DO NOTHING;

-- 1. Fundacion de prueba
INSERT INTO FUNDACIONES (ruc, nombre, mision, area_intervencion, activa, tiene_convenio)
SELECT '1799999999001', 'Fundación de Prueba UNIBE', 'Datos de prueba del sistema', 'Educación', TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM FUNDACIONES WHERE ruc = '1799999999001');

-- 2. Proyecto de prueba
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

-- 3. Vinculacion completada del estudiante de prueba (periodo anterior)
INSERT INTO VINCULACION
    (estudiante_id, fundacion_id, proyecto_id, estado,
     horas_requeridas, horas_completadas, fecha_inicio, fecha_fin, periodo_academico)
SELECT e.id, f.id, p.id, 'completado', 160, 160,
       '2026-01-15', '2026-05-30', '2026-1'
FROM ESTUDIANTES e
JOIN FUNDACIONES f ON f.ruc = '1799999999001'
JOIN PROYECTOS p ON p.nombre = 'Proyecto de Prueba - Alfabetización Digital'
WHERE e.matricula = 'EST-2026-0001'
  AND NOT EXISTS (
      SELECT 1 FROM VINCULACION v
      WHERE v.estudiante_id = e.id AND v.estado = 'completado'
  );

-- ============================================================
-- Verificacion: debe mostrar la vinculacion completada
-- ============================================================
SELECT v.id, e.matricula, v.estado, v.horas_completadas, v.horas_requeridas,
       f.nombre AS fundacion, p.nombre AS proyecto
FROM VINCULACION v
JOIN ESTUDIANTES e ON e.id = v.estudiante_id
JOIN FUNDACIONES f ON f.id = v.fundacion_id
JOIN PROYECTOS p ON p.id = v.proyecto_id
WHERE e.matricula = 'EST-2026-0001';
