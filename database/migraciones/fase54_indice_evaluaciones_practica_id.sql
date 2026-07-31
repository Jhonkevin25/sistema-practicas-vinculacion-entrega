-- Fase 54: indice en practica_id de EVALUACIONES_PRACTICAS_DETALLE (2026-07-28)
-- Ejecutar en el SQL Editor de Supabase.
--
-- Motivo:
-- - EVALUACIONES_PRACTICAS_DETALLE ya tenia indice en vinculacion_id
--   (idx_eval_detalle_vinculacion, agregado en fase17_paridad_vinculacion.sql)
--   pero no en practica_id. El listado de seguimiento de practicas dispara
--   una query findByPracticaId sobre esta tabla por cada practica listada
--   (problema N+1 ya conocido y no resuelto por este script), y sin indice
--   cada una de esas queries hace un full scan de la tabla en lugar de un
--   index scan.
--
-- Impacto:
-- - Agrega el indice idx_eval_detalle_practica sobre
--   EVALUACIONES_PRACTICAS_DETALLE(practica_id).
-- - No cambia columnas ni constraints: no afecta a ninguna entidad JPA
--   (EvaluacionPracticaDetalle no requiere cambios; ddl-auto=validate no
--   valida indices, solo columnas/tablas/constraints mapeadas).
-- - Mejora el tiempo de respuesta del listado de seguimiento de practicas:
--   cada findByPracticaId individual deja de ser un full scan, aunque el
--   patron N+1 en si (una query por practica) sigue existiendo y quedaria
--   pendiente como mejora aparte (p.ej. batch fetch o join).
--
-- Entidades JPA afectadas: ninguna.
--
-- Script idempotente: puede ejecutarse dos veces sin error.

BEGIN;

CREATE INDEX IF NOT EXISTS idx_eval_detalle_practica
ON EVALUACIONES_PRACTICAS_DETALLE(practica_id);

COMMIT;

-- Validacion: el indice debe existir.
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'evaluaciones_practicas_detalle'
  AND indexname = 'idx_eval_detalle_practica';
