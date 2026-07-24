-- ============================================================
-- FASE 27 — CATALOGO DE CARRERAS
-- Ejecutar en el SQL Editor de Supabase. Es idempotente.
-- ============================================================
-- Cambio:  tabla CARRERAS como catalogo unico de carreras; se siembra
--          con las carreras ya usadas en el sistema y las carreras base.
-- Motivo:  eliminar el texto libre (errores de tildes/escritura) en
--          estudiantes, convenios, vacantes y alcance del coordinador.
-- Impacto: tabla nueva CARRERAS. No modifica columnas existentes: las
--          demas tablas siguen guardando la carrera como texto y el
--          frontend pasa a elegirla del catalogo.

CREATE TABLE IF NOT EXISTS CARRERAS (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR(200) NOT NULL UNIQUE,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 1. Carreras institucionales base. El ADMIN puede agregar otras desde la UI.
INSERT INTO CARRERAS (nombre) VALUES
    ('Derecho'),
    ('Enfermería'),
    ('Estética Integral'),
    ('Fisioterapia'),
    ('Gastronomía'),
    ('Ingeniería en Software'),
    ('Medicina'),
    ('Multimedia y Producción Audiovisual'),
    ('Nutrición y Dietética'),
    ('Odontología'),
    ('Psicología'),
    ('Psicología Clínica')
ON CONFLICT (nombre) DO NOTHING;

-- 2. Carreras ya usadas en los datos existentes (no perder ninguna)
INSERT INTO CARRERAS (nombre)
SELECT DISTINCT TRIM(carrera) FROM ESTUDIANTES
WHERE carrera IS NOT NULL AND TRIM(carrera) <> ''
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO CARRERAS (nombre)
SELECT DISTINCT TRIM(carrera) FROM COORDINADOR_CARRERAS
WHERE carrera IS NOT NULL AND TRIM(carrera) <> ''
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO CARRERAS (nombre)
SELECT DISTINCT TRIM(carrera) FROM CONVENIOS_CARRERAS
WHERE carrera IS NOT NULL AND TRIM(carrera) <> ''
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO CARRERAS (nombre)
SELECT DISTINCT TRIM(carrera) FROM VACANTES_PRACTICAS
WHERE carrera IS NOT NULL AND TRIM(carrera) <> ''
ON CONFLICT (nombre) DO NOTHING;

-- ============================================================
-- Verificacion
-- ============================================================
SELECT id, nombre, activo FROM CARRERAS ORDER BY nombre;
