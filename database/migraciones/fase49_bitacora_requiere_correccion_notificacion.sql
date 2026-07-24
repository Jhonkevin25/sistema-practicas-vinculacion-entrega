-- Fase 49: notificacion de "bitacora requiere correccion" (2026-07-20)
-- Ejecutar en el SQL Editor de Supabase.
--
-- Objetivo:
-- - completar el flujo del estado 'requiere_correccion' de BITACORAS: hasta
--   ahora el estado existia en la entidad/validacion pero no generaba ninguna
--   notificacion al estudiante (a diferencia de 'rechazada', que si la tiene).
-- - agregar el tipo 'bitacora_requiere_correccion' al CHECK de NOTIFICACIONES.
--
-- Script idempotente: puede ejecutarse dos veces sin error. El constraint se
-- elimina y se vuelve a crear (no basta un "IF NOT EXISTS" simple, porque el
-- constraint ya existe con la lista vieja y quedaria sin el valor nuevo).

BEGIN;

ALTER TABLE NOTIFICACIONES
    DROP CONSTRAINT IF EXISTS chk_notificaciones_tipo;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_notificaciones_tipo'
    ) THEN
        ALTER TABLE NOTIFICACIONES
        ADD CONSTRAINT chk_notificaciones_tipo
        CHECK (tipo IN (
            'documento_aprobado',
            'documento_rechazado',
            'postulacion_enviada',
            'postulacion_resuelta',
            'asignacion_practica',
            'asignacion_vinculacion',
            'bitacora_rechazada',
            'bitacora_requiere_correccion',
            'nota_registrada',
            'encuesta_habilitada',
            'expediente_por_cerrar',
            'expediente_cerrado'
        ));
    END IF;
END $$;

COMMIT;

-- Validacion final: debe devolver 0 filas.
SELECT id, usuario_destino_id, tipo
FROM NOTIFICACIONES
WHERE tipo NOT IN (
    'documento_aprobado',
    'documento_rechazado',
    'postulacion_enviada',
    'postulacion_resuelta',
    'asignacion_practica',
    'asignacion_vinculacion',
    'bitacora_rechazada',
    'bitacora_requiere_correccion',
    'nota_registrada',
    'encuesta_habilitada',
    'expediente_por_cerrar',
    'expediente_cerrado'
);
