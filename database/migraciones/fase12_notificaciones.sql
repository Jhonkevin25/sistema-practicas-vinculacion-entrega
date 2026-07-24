-- Fase 12: notificaciones internas (2026-07-08)
-- Ejecutar en el SQL Editor de Supabase.
--
-- Objetivo:
-- - crear la tabla NOTIFICACIONES como base de la bandeja interna por usuario
-- - registrar eventos del sistema (documentos, postulaciones, asignaciones,
--   bitacoras, notas, encuestas y cierre de expediente) dirigidos a un usuario
-- - permitir marcar como leida y contar no leidas de forma eficiente
-- - guardar una referencia opcional (tipo + id) y un link interno del frontend
--
-- Script idempotente: puede ejecutarse dos veces sin error.

BEGIN;

CREATE TABLE IF NOT EXISTS NOTIFICACIONES (
    id                  SERIAL PRIMARY KEY,
    usuario_destino_id  INT NOT NULL REFERENCES USUARIOS(id) ON DELETE CASCADE,
    tipo                VARCHAR(40) NOT NULL,
    titulo              VARCHAR(200) NOT NULL,
    mensaje             TEXT NOT NULL,
    leida               BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_creacion      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    referencia_tipo     VARCHAR(40),
    referencia_id       BIGINT,
    link                VARCHAR(300)
);

-- Vocabulario de tipos en snake_case minusculas, igual que los estados de
-- BITACORAS y DOCS_ESTUDIANTE ('pendiente', 'aprobada', 'documento_aprobado'...).
-- Reservados sin emisor todavia: documento_aprobado/documento_rechazado (la
-- revision documental llega en la fase 16) y expediente_por_cerrar (fase 18).
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
            'nota_registrada',
            'encuesta_habilitada',
            'expediente_por_cerrar',
            'expediente_cerrado'
        ));
    END IF;
END $$;

-- Indice principal de la bandeja: listar/contar por destinatario y estado de lectura.
CREATE INDEX IF NOT EXISTS idx_notificaciones_destino_leida
ON NOTIFICACIONES(usuario_destino_id, leida);

-- Nota: NO se agrega trigger de auditoria (fn_auditoria) sobre NOTIFICACIONES.
-- Es una tabla derivada de alto volumen; auditarla duplicaria cada evento en
-- AUDITORIA sin aportar trazabilidad de negocio (los eventos de origen ya se auditan).

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
    'nota_registrada',
    'encuesta_habilitada',
    'expediente_por_cerrar',
    'expediente_cerrado'
);
