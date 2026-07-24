-- ============================================================
-- FASE 14 - Recuperacion de contraseña
-- Sistema de Practicas y Vinculacion UNIBE
-- ============================================================
-- Objetivo:
-- - guardar tokens de recuperacion de contraseña con hash, expiracion corta
--   y marca de un solo uso
-- - soportar POST /api/auth/recuperar y POST /api/auth/restablecer
-- - no guardar el token plano en base de datos
--
-- Script idempotente: puede ejecutarse mas de una vez sin error.

CREATE TABLE IF NOT EXISTS TOKENS_RECUPERACION (
    id                  SERIAL PRIMARY KEY,
    usuario_id          INT NOT NULL REFERENCES USUARIOS(id) ON DELETE CASCADE,
    token_hash          VARCHAR(128) NOT NULL UNIQUE,
    fecha_expiracion    TIMESTAMP NOT NULL,
    usado               BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_creacion      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    usado_en            TIMESTAMP,
    CHECK (fecha_expiracion > fecha_creacion)
);

CREATE INDEX IF NOT EXISTS idx_tokens_recuperacion_usuario
ON TOKENS_RECUPERACION(usuario_id);

CREATE INDEX IF NOT EXISTS idx_tokens_recuperacion_vigente
ON TOKENS_RECUPERACION(token_hash, usado, fecha_expiracion);

-- Validacion final: debe devolver 0 filas.
SELECT id, usuario_id, usado, fecha_creacion, fecha_expiracion
FROM TOKENS_RECUPERACION
WHERE fecha_expiracion <= fecha_creacion;
