-- Corrección puntual de datos de prueba (2026-07-23)
-- Ejecutar en el SQL Editor de Supabase.
--
-- Contexto: las vacantes de Práctica (I y II) se crearon con 240 horas
-- requeridas por un valor por defecto incorrecto del formulario. El requisito
-- real de UNIBE es 120h por Práctica (I + II = 240h, + 160h de Vinculación =
-- 400h totales). Este script corrige las vacantes y las prácticas ya
-- consolidadas que quedaron con 240h.
--
-- Script idempotente: puede ejecutarse dos veces sin error (solo actualiza
-- filas que todavía tengan el valor incorrecto 240).

BEGIN;

UPDATE VACANTES_PRACTICAS
SET horas = 120
WHERE horas = 240;

UPDATE PRACTICAS
SET horas_requeridas = 120
WHERE horas_requeridas = 240;

COMMIT;

-- Validacion final: debe devolver 0 filas en ambas consultas.
SELECT id, nombre, horas FROM VACANTES_PRACTICAS WHERE horas = 240;
SELECT id, estudiante_id, horas_requeridas FROM PRACTICAS WHERE horas_requeridas = 240;
