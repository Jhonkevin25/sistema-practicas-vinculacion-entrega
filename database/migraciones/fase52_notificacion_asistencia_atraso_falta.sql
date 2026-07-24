-- Fase 52: notificacion automatica de atraso/falta de asistencia (2026-07-23)
-- Ejecutar en el SQL Editor de Supabase.
--
-- Objetivo:
-- - agregar los tipos 'asistencia_atraso' y 'asistencia_falta' al vocabulario
--   de NOTIFICACIONES, usados cuando el tutor marca una asistencia como
--   Atraso o Falta (AsistenciaController): el estudiante recibe un aviso
--   automatico (in-app + correo encolado) recordandole asistir puntualmente.
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
            'expediente_cerrado',
            'asistencia_atraso',
            'asistencia_falta'
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
    'expediente_cerrado',
    'asistencia_atraso',
    'asistencia_falta'
);
