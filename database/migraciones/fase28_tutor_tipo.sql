-- ============================================================
-- FASE 28 — TIPO REAL DEL TUTOR (PRACTICAS / VINCULACION / AMBOS)
-- Ejecutar en el SQL Editor de Supabase. Es idempotente.
-- ============================================================
-- Cambio:  columna usuarios.tutor_tipo con CHECK; reemplaza el toggle
--          demo del frontend (localStorage pravi_tutor_tipo).
-- Motivo:  el tipo de tutoria es un dato institucional que asigna el
--          ADMIN, no una simulacion del navegador.
-- Impacto: tabla USUARIOS. Los tutores existentes quedan en AMBOS
--          (equivalente al comportamiento previo del demo).

ALTER TABLE USUARIOS ADD COLUMN IF NOT EXISTS tutor_tipo VARCHAR(20);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'usuarios_tutor_tipo_check'
    ) THEN
        ALTER TABLE USUARIOS
            ADD CONSTRAINT usuarios_tutor_tipo_check
            CHECK (tutor_tipo IS NULL OR tutor_tipo IN ('PRACTICAS', 'VINCULACION', 'AMBOS'));
    END IF;
END $$;

-- Tutores existentes sin tipo: AMBOS
UPDATE USUARIOS u
SET tutor_tipo = 'AMBOS'
WHERE u.tutor_tipo IS NULL
  AND EXISTS (
      SELECT 1
      FROM USUARIOS_ROLES ur
      JOIN ROLES r ON r.id = ur.rol_id
      WHERE ur.usuario_id = u.id AND r.codigo = 'TUTOR'
  );

-- ============================================================
-- Verificacion
-- ============================================================
SELECT u.email, u.tutor_tipo
FROM USUARIOS u
WHERE u.tutor_tipo IS NOT NULL
ORDER BY u.email;
