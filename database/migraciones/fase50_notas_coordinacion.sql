-- Fase 50: notas de coordinacion (constancia libre del coordinador visible al estudiante) (2026-07-20)
-- Ejecutar en el SQL Editor de Supabase.
--
-- Objetivo:
-- - permitir que el coordinador (o admin) deje una nota de texto libre por
--   expediente (practica O vinculacion, XOR), append-only, sin editar/eliminar.
-- - el estudiante la ve en su expediente academico y recibe notificacion+correo.
--
-- Script idempotente: puede ejecutarse dos veces sin error.

BEGIN;

CREATE TABLE IF NOT EXISTS NOTAS_COORDINACION (
    id               SERIAL PRIMARY KEY,
    estudiante_id    INT NOT NULL REFERENCES ESTUDIANTES(id) ON DELETE CASCADE,
    practica_id      INT REFERENCES PRACTICAS(id) ON DELETE CASCADE,
    vinculacion_id   INT REFERENCES VINCULACION(id) ON DELETE CASCADE,
    autor_id         INT NOT NULL REFERENCES USUARIOS(id),
    mensaje          TEXT NOT NULL,
    fecha_creacion   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- XOR: exactamente una de practica_id/vinculacion_id, igual criterio que
-- BITACORAS/ASISTENCIAS.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_notas_coordinacion_expediente'
    ) THEN
        ALTER TABLE NOTAS_COORDINACION
        ADD CONSTRAINT chk_notas_coordinacion_expediente
        CHECK (
            (practica_id IS NOT NULL AND vinculacion_id IS NULL)
            OR (practica_id IS NULL AND vinculacion_id IS NOT NULL)
        );
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_notas_coordinacion_estudiante
ON NOTAS_COORDINACION(estudiante_id);

-- Agregar 'nota_coordinacion' al vocabulario de NOTIFICACIONES (mismo patron
-- DROP+ADD de fase49, porque el "IF NOT EXISTS" simple dejaria el constraint
-- viejo intacto).
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
            'encuesta_habilitada',
            'expediente_por_cerrar',
            'expediente_cerrado'
        ));
    END IF;
END $$;

COMMIT;

-- Validacion final: debe devolver 0 filas.
SELECT id, estudiante_id, practica_id, vinculacion_id
FROM NOTAS_COORDINACION
WHERE (practica_id IS NOT NULL) = (vinculacion_id IS NOT NULL);

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
    'encuesta_habilitada',
    'expediente_por_cerrar',
    'expediente_cerrado'
);
