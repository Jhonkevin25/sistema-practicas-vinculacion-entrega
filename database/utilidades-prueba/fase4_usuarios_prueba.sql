-- ============================================================
-- FASE 4 — USUARIOS DE PRUEBA POR ROL + CONVOCATORIA VIGENTE
-- Ejecutar en Supabase SQL Editor. Es idempotente (ON CONFLICT).
-- ============================================================
-- Cambio:  crea usuarios de prueba COORDINADOR/TUTOR/ESTUDIANTE,
--          el registro de estudiante asociado y una convocatoria 2026-2 vigente.
-- Motivo:  el seed original solo crea el usuario ADMIN y las fechas de
--          convocatoria 2025-1 ya expiraron; sin esto no se puede validar
--          el flujo MVP por rol (Fase 4 del plan).
-- Impacto: tablas USUARIOS, USUARIOS_ROLES, ESTUDIANTES, FECHAS_CONVOCATORIA.
--
-- Credenciales de prueba (solo para desarrollo, no usar en produccion):
--   coordinador@sistema.edu.ec  /  Coordinador2026*
--   tutor@sistema.edu.ec        /  Tutor2026*
--   estudiante@sistema.edu.ec   /  Estudiante2026*

-- pgcrypto: crypt(..., gen_salt('bf')) genera hashes $2a$ compatibles
-- con BCryptPasswordEncoder de Spring Security
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. Usuarios (NOT EXISTS por email O cedula: evita chocar con la
--    restriccion unica del email si ya existe con otra cedula)
INSERT INTO USUARIOS (cedula, nombre, apellido, email, password_hash, activo, primer_login)
SELECT v.cedula, v.nombre, v.apellido, v.email,
       crypt(v.pass, gen_salt('bf', 10)), TRUE, FALSE
FROM (VALUES
    ('0000000002', 'Carla', 'Coordinadora', 'coordinador@sistema.edu.ec', 'Coordinador2026*'),
    ('0000000003', 'Tomas', 'Tutor',        'tutor@sistema.edu.ec',       'Tutor2026*'),
    ('0000000004', 'Elena', 'Estudiante',   'estudiante@sistema.edu.ec',  'Estudiante2026*')
) AS v(cedula, nombre, apellido, email, pass)
WHERE NOT EXISTS (
    SELECT 1 FROM USUARIOS u
    WHERE u.email = v.email OR u.cedula = v.cedula
);

-- 2. Asignacion de roles
INSERT INTO USUARIOS_ROLES (usuario_id, rol_id, asignado_por)
SELECT u.id, r.id, (SELECT id FROM USUARIOS WHERE cedula = '0000000001')
FROM USUARIOS u
JOIN ROLES r ON (
    (u.cedula = '0000000002' AND r.codigo = 'COORDINADOR') OR
    (u.cedula = '0000000003' AND r.codigo = 'TUTOR') OR
    (u.cedula = '0000000004' AND r.codigo = 'ESTUDIANTE')
)
WHERE NOT EXISTS (
    SELECT 1 FROM USUARIOS_ROLES ur WHERE ur.usuario_id = u.id AND ur.rol_id = r.id
);

INSERT INTO PERIODOS_ACADEMICOS (codigo, fecha_inicio, fecha_fin, estado)
VALUES ('2026-2', '2026-07-01', '2026-12-31', 'ACTIVO')
ON CONFLICT (codigo) DO NOTHING;

-- 3. Registro academico del estudiante de prueba
INSERT INTO ESTUDIANTES (usuario_id, matricula, carrera, semestre, periodo_academico)
SELECT u.id, 'EST-2026-0001', 'Ingeniería en Software', 6, '2026-2'
FROM USUARIOS u
WHERE u.cedula = '0000000004'
ON CONFLICT (matricula) DO NOTHING;

-- 4. Convocatoria vigente (las de 2025 ya expiraron)
-- Orden que exige el backend: inicio <= limite documentos <= inicio postulacion <= cierre.
INSERT INTO FECHAS_CONVOCATORIA
    (periodo_academico, tipo, convocatoria_inicio, convocatoria_fin,
     fecha_limite_documentos, fecha_inicio_postulacion)
VALUES
    ('2026-2', 'PRACTICAS',   '2026-06-01', '2026-12-31', '2026-07-01', '2026-07-01'),
    ('2026-2', 'VINCULACION', '2026-06-01', '2026-12-31', '2026-07-01', '2026-07-01')
ON CONFLICT (periodo_academico, tipo) DO NOTHING;

-- 4b. Reparar filas existentes con orden invalido (una version anterior de este
-- script insertaba postulacion antes del limite de documentos y el backend
-- rechaza con 400 cualquier guardado que conserve esos valores).
UPDATE FECHAS_CONVOCATORIA
SET convocatoria_inicio     = '2026-06-01',
    convocatoria_fin        = '2026-12-31',
    fecha_limite_documentos = '2026-07-01',
    fecha_inicio_postulacion = '2026-07-01'
WHERE periodo_academico = '2026-2'
  AND (fecha_inicio_postulacion < fecha_limite_documentos
       OR fecha_limite_documentos < convocatoria_inicio
       OR convocatoria_fin < fecha_inicio_postulacion);

-- (Opcional) Restablecer la contraseña del ADMIN si no puedes iniciar sesion.
-- Descomentar y ajustar:
-- UPDATE USUARIOS
-- SET password_hash = crypt('Admin2026*', gen_salt('bf', 10))
-- WHERE cedula = '0000000001';

-- ============================================================
-- Verificacion
-- ============================================================
SELECT u.cedula, u.email, u.activo, r.codigo AS rol,
       e.matricula, e.carrera
FROM USUARIOS u
LEFT JOIN USUARIOS_ROLES ur ON ur.usuario_id = u.id
LEFT JOIN ROLES r ON r.id = ur.rol_id
LEFT JOIN ESTUDIANTES e ON e.usuario_id = u.id
WHERE u.cedula IN ('0000000001','0000000002','0000000003','0000000004')
ORDER BY u.cedula;

SELECT periodo_academico, tipo, convocatoria_inicio, convocatoria_fin,
       fecha_limite_documentos, fecha_inicio_postulacion
FROM FECHAS_CONVOCATORIA
ORDER BY periodo_academico DESC, tipo;
