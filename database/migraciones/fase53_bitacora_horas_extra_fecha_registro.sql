-- Fase 53: bitacoras - horas extra y fecha real de registro (2026-07-28)
-- Ejecutar en el SQL Editor de Supabase.
--
-- Motivo:
-- - la bitacora diaria de un estudiante se va a validar contra su registro de
--   ASISTENCIAS del mismo dia (columnas hora_ingreso/hora_salida, VARCHAR(8)
--   formato "HH:mm"): si el estudiante reporta trabajar mas alla de su
--   horario de asistencia, esas horas adicionales se guardan aparte en
--   horas_extra en lugar de mezclarlas con "horas" (que sigue representando
--   las horas dentro del horario de asistencia del dia).
-- - fecha_registro guarda el dia real en que se creo el registro de bitacora,
--   a diferencia de "fecha" (que es el dia de la actividad reportada); sirve
--   para detectar si la bitacora se lleno el mismo dia o despues.
--
-- Impacto:
-- - BITACORAS gana dos columnas:
--   - horas_extra INT NOT NULL DEFAULT 0, CHECK (horas_extra >= 0).
--   - fecha_registro DATE, con backfill = fecha para las filas existentes
--     (se asume que las bitacoras historicas se llenaron el mismo dia) y
--     default CURRENT_DATE para filas nuevas (el backend la va a setear
--     explicitamente al crear el registro, el default es solo un respaldo).
-- - horas_extra debe sumarse a "horas" al calcular el total de horas
--   completadas del expediente (fase35_horas_oficiales_derivadas.sql): este
--   script reemplaza fn_horas_aprobadas_practica/fn_horas_aprobadas_vinculacion
--   para que sumen "horas + horas_extra" de las bitacoras aprobadas, y agrega
--   horas_extra a la lista de columnas que disparan
--   trg_sincronizar_horas_bitacora (para que un UPDATE que solo cambie
--   horas_extra tambien recalcule horas_completadas en PRACTICAS/VINCULACION).
--   CREATE OR REPLACE FUNCTION/TRIGGER es idempotente por naturaleza en
--   Postgres.
--
-- Entidades JPA afectadas (deben actualizarse junto con este script, porque
-- ddl-auto=validate rompe el arranque si divergen del esquema):
-- - ec.edu.unibe.sistema_practicas.bitacora.Bitacora (agregar los campos
--   horasExtra y fechaRegistro con sus @Column correspondientes).
--
-- Script idempotente: puede ejecutarse dos veces sin error.

BEGIN;

ALTER TABLE BITACORAS
    ADD COLUMN IF NOT EXISTS horas_extra INT NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_bitacoras_horas_extra_no_negativas'
    ) THEN
        ALTER TABLE BITACORAS
        ADD CONSTRAINT chk_bitacoras_horas_extra_no_negativas
        CHECK (horas_extra >= 0);
    END IF;
END $$;

ALTER TABLE BITACORAS
    ADD COLUMN IF NOT EXISTS fecha_registro DATE;

-- Backfill: para las bitacoras existentes se asume que se llenaron el mismo
-- dia de la actividad reportada (fecha_registro = fecha).
UPDATE BITACORAS
SET fecha_registro = fecha
WHERE fecha_registro IS NULL;

-- Default para filas nuevas (respaldo; el backend setea el valor explicito
-- al crear el registro).
ALTER TABLE BITACORAS
    ALTER COLUMN fecha_registro SET DEFAULT CURRENT_DATE;

-- Recalculo de horas aprobadas: suma "horas + horas_extra" en vez de solo
-- "horas". Mismo cuerpo que fase35_horas_oficiales_derivadas.sql, con la
-- suma agregada.
CREATE OR REPLACE FUNCTION fn_horas_aprobadas_practica(
    expediente_id INT,
    horas_requeridas_expediente INT
)
RETURNS INT AS $$
    SELECT LEAST(horas_requeridas_expediente, COALESCE(SUM(b.horas + b.horas_extra), 0)::INT)
    FROM BITACORAS b
    WHERE b.practica_id = expediente_id AND b.estado = 'aprobada';
$$ LANGUAGE sql STABLE;

CREATE OR REPLACE FUNCTION fn_horas_aprobadas_vinculacion(
    expediente_id INT,
    horas_requeridas_expediente INT
)
RETURNS INT AS $$
    SELECT LEAST(horas_requeridas_expediente, COALESCE(SUM(b.horas + b.horas_extra), 0)::INT)
    FROM BITACORAS b
    WHERE b.vinculacion_id = expediente_id AND b.estado = 'aprobada';
$$ LANGUAGE sql STABLE;

-- El trigger tambien debe dispararse cuando cambia horas_extra (antes solo
-- reaccionaba a horas, estado, practica_id, vinculacion_id o DELETE).
CREATE OR REPLACE TRIGGER trg_sincronizar_horas_bitacora
    AFTER INSERT OR UPDATE OF horas, horas_extra, estado, practica_id, vinculacion_id OR DELETE
    ON BITACORAS
    FOR EACH ROW EXECUTE FUNCTION fn_sincronizar_horas_desde_bitacora();

COMMIT;

-- Validacion final: ambas consultas deben devolver 0 filas.
SELECT id, estudiante_id, horas_extra
FROM BITACORAS
WHERE horas_extra < 0;

SELECT id, estudiante_id, fecha, fecha_registro
FROM BITACORAS
WHERE fecha_registro IS NULL;

-- Validacion adicional: horas_completadas de expedientes con bitacoras
-- aprobadas debe coincidir con el nuevo calculo (horas + horas_extra). Debe
-- devolver 0 filas.
SELECT p.id, p.horas_completadas, fn_horas_aprobadas_practica(p.id, p.horas_requeridas) AS esperado
FROM PRACTICAS p
WHERE p.horas_completadas <> fn_horas_aprobadas_practica(p.id, p.horas_requeridas);

SELECT v.id, v.horas_completadas, fn_horas_aprobadas_vinculacion(v.id, v.horas_requeridas) AS esperado
FROM VINCULACION v
WHERE v.horas_completadas <> fn_horas_aprobadas_vinculacion(v.id, v.horas_requeridas);
