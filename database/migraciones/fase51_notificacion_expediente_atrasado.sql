-- Fase 51: notificacion automatica de atraso al estudiante (2026-07-20)
-- Ejecutar en el SQL Editor de Supabase.
--
-- Objetivo:
-- - agregar el tipo 'expediente_atrasado' al vocabulario de NOTIFICACIONES,
--   usado por el job diario (AlertaAtrasoScheduler) que avisa al estudiante
--   por correo+sistema cuando lleva 14+ dias sin actividad en bitacoras,
--   sin que el coordinador tenga que notificarlo manualmente.
--
-- Script idempotente: puede ejecutarse dos veces sin error.

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
            'nota_coordinacion',
            'expediente_atrasado',
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
    'nota_coordinacion',
    'expediente_atrasado',
    'encuesta_habilitada',
    'expediente_por_cerrar',
    'expediente_cerrado'
);
