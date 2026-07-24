-- Fase 18: cierre formal robusto.
-- Idempotente: agrega metadatos persistentes del cierre para prácticas
-- y vinculación. El snapshot también se registra en AUDITORIA desde backend.

ALTER TABLE PRACTICAS
ADD COLUMN IF NOT EXISTS cerrado_por INT REFERENCES USUARIOS(id) ON DELETE SET NULL;

ALTER TABLE PRACTICAS
ADD COLUMN IF NOT EXISTS cerrado_en TIMESTAMP;

ALTER TABLE PRACTICAS
ADD COLUMN IF NOT EXISTS cierre_snapshot JSONB;

ALTER TABLE VINCULACION
ADD COLUMN IF NOT EXISTS cerrado_por INT REFERENCES USUARIOS(id) ON DELETE SET NULL;

ALTER TABLE VINCULACION
ADD COLUMN IF NOT EXISTS cerrado_en TIMESTAMP;

ALTER TABLE VINCULACION
ADD COLUMN IF NOT EXISTS cierre_snapshot JSONB;

CREATE INDEX IF NOT EXISTS idx_practicas_cerrado_en
ON PRACTICAS(cerrado_en)
WHERE cerrado_en IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_vinculacion_cerrado_en
ON VINCULACION(cerrado_en)
WHERE cerrado_en IS NOT NULL;
