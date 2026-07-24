-- ============================================================
-- FASE 36 - ENDURECIMIENTO DEL INICIO DE SESION
-- ============================================================
-- Ejecutar completo en el SQL Editor de Supabase. Es idempotente.
-- No desactiva usuarios ni invalida sesiones existentes.

BEGIN;

ALTER TABLE USUARIOS
    ADD COLUMN IF NOT EXISTS intentos_login_fallidos INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS bloqueado_hasta TIMESTAMP;

UPDATE USUARIOS
SET intentos_login_fallidos = 0
WHERE intentos_login_fallidos IS NULL;

ALTER TABLE USUARIOS
    ALTER COLUMN intentos_login_fallidos SET DEFAULT 0,
    ALTER COLUMN intentos_login_fallidos SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'USUARIOS'::regclass
          AND conname = 'chk_usuarios_intentos_login_no_negativos'
    ) THEN
        ALTER TABLE USUARIOS
            ADD CONSTRAINT chk_usuarios_intentos_login_no_negativos
            CHECK (intentos_login_fallidos >= 0);
    END IF;
END $$;

ALTER TABLE USUARIOS
    VALIDATE CONSTRAINT chk_usuarios_intentos_login_no_negativos;

COMMIT;

-- Resultado esperado: dos columnas, contador NOT NULL y bloqueo nullable.
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'usuarios'
  AND column_name IN ('intentos_login_fallidos', 'bloqueado_hasta')
ORDER BY column_name;

-- Resultado esperado: una fila con validada = true.
SELECT conname AS nombre_constraint, convalidated AS validada
FROM pg_constraint
WHERE conrelid = 'USUARIOS'::regclass
  AND conname = 'chk_usuarios_intentos_login_no_negativos';

-- Resultado esperado: invalidos = 0.
SELECT COUNT(*) AS invalidos
FROM USUARIOS
WHERE intentos_login_fallidos < 0;
