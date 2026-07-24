-- ============================================================
-- MIGRACIÓN v2 — Correcciones Auditoría ISO/IEC 25010
-- Sistema de Prácticas y Vinculación UNIBE
-- PostgreSQL 15 — Supabase SQL Editor
-- 
-- INSTRUCCIONES:
-- 1. Abrir Supabase Dashboard → SQL Editor
-- 2. Copiar y pegar este script completo
-- 3. Ejecutar (Run)
-- 4. Verificar que no haya errores
-- ============================================================

-- ============================================================
-- D5: Agregar columna perfil_requerido a VACANTES_PRACTICAS
-- Descripción del perfil profesional que necesita el estudiante
-- ============================================================

ALTER TABLE VACANTES_PRACTICAS
ADD COLUMN IF NOT EXISTS perfil_requerido TEXT;

COMMENT ON COLUMN VACANTES_PRACTICAS.perfil_requerido 
IS 'Describe las habilidades, conocimientos y competencias requeridas del estudiante para esta vacante';

-- ============================================================
-- D6: Nueva tabla FECHAS_CONVOCATORIA
-- Gestiona los períodos de carga documental y postulación
-- ============================================================

CREATE TABLE IF NOT EXISTS FECHAS_CONVOCATORIA (
    id                          SERIAL PRIMARY KEY,
    periodo_academico           VARCHAR(20) NOT NULL,
    tipo                        VARCHAR(30) NOT NULL CHECK (tipo IN ('PRACTICAS', 'VINCULACION')),
    convocatoria_inicio         DATE NOT NULL,
    convocatoria_fin            DATE NOT NULL,
    fecha_limite_documentos     DATE NOT NULL,
    fecha_inicio_postulacion    DATE NOT NULL,
    creado_por                  INT REFERENCES USUARIOS(id),
    created_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(periodo_academico, tipo)
);

COMMENT ON TABLE FECHAS_CONVOCATORIA 
IS 'Fechas de convocatoria por período académico y tipo (prácticas/vinculación)';

-- Insertar datos iniciales de convocatoria
INSERT INTO PERIODOS_ACADEMICOS (codigo, fecha_inicio, fecha_fin, estado)
VALUES ('2025-1', '2025-01-01', '2025-06-30', 'CERRADO')
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO FECHAS_CONVOCATORIA (periodo_academico, tipo, convocatoria_inicio, convocatoria_fin, fecha_limite_documentos, fecha_inicio_postulacion)
VALUES 
    ('2025-1', 'PRACTICAS', '2025-06-01', '2025-07-31', '2025-06-20', '2025-06-25'),
    ('2025-1', 'VINCULACION', '2025-06-01', '2025-07-31', '2025-06-20', '2025-06-25')
ON CONFLICT (periodo_academico, tipo) DO NOTHING;

-- ============================================================
-- D7: Índices de rendimiento (ISO 25010 — 1.2 Eficiencia)
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_estudiantes_carrera ON ESTUDIANTES(carrera);
CREATE INDEX IF NOT EXISTS idx_practicas_estado ON PRACTICAS(estado);
CREATE INDEX IF NOT EXISTS idx_practicas_estudiante ON PRACTICAS(estudiante_id);
CREATE INDEX IF NOT EXISTS idx_vinculacion_estado ON VINCULACION(estado);
CREATE INDEX IF NOT EXISTS idx_vinculacion_estudiante ON VINCULACION(estudiante_id);
CREATE INDEX IF NOT EXISTS idx_bitacoras_estudiante ON BITACORAS(estudiante_id);
CREATE INDEX IF NOT EXISTS idx_asistencias_estudiante ON ASISTENCIAS(estudiante_id);
CREATE INDEX IF NOT EXISTS idx_postulaciones_estudiante ON POSTULACIONES_MERITOCRATICAS(estudiante_id);
CREATE INDEX IF NOT EXISTS idx_vacantes_carrera ON VACANTES_PRACTICAS(carrera);

-- ============================================================
-- D8: Triggers de auditoría faltantes (ISO 25010 — 1.6 Trazabilidad)
-- ============================================================

CREATE OR REPLACE TRIGGER trg_auditoria_bitacoras
    AFTER INSERT OR UPDATE OR DELETE ON BITACORAS
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria();

CREATE OR REPLACE TRIGGER trg_auditoria_asistencias
    AFTER INSERT OR UPDATE OR DELETE ON ASISTENCIAS
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria();

CREATE OR REPLACE TRIGGER trg_auditoria_evaluaciones_detalle
    AFTER INSERT OR UPDATE OR DELETE ON EVALUACIONES_PRACTICAS_DETALLE
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria();

CREATE OR REPLACE TRIGGER trg_auditoria_fechas_convocatoria
    AFTER INSERT OR UPDATE OR DELETE ON FECHAS_CONVOCATORIA
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria();

-- ============================================================
-- VERIFICACIÓN
-- ============================================================

-- Verificar que la columna fue creada
SELECT column_name, data_type 
FROM information_schema.columns 
WHERE table_name = 'vacantes_practicas' AND column_name = 'perfil_requerido';

-- Verificar que la tabla fue creada
SELECT table_name 
FROM information_schema.tables 
WHERE table_name = 'fechas_convocatoria';

-- Verificar índices creados
SELECT indexname 
FROM pg_indexes 
WHERE tablename IN ('estudiantes', 'practicas', 'vinculacion', 'bitacoras', 'asistencias', 'postulaciones_meritocraticas', 'vacantes_practicas')
ORDER BY indexname;

-- Verificar triggers
SELECT trigger_name, event_object_table 
FROM information_schema.triggers 
WHERE trigger_name LIKE 'trg_auditoria_%'
ORDER BY trigger_name;
