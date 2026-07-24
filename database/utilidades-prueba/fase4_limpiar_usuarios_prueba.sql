-- ============================================================
-- LIMPIEZA DE USUARIOS DE PRUEBA (por email)
-- Ejecutar en Supabase SQL Editor ANTES de fase4_usuarios_prueba.sql
-- ============================================================
-- Cambio:  elimina los usuarios de prueba (coordinador/tutor/estudiante)
--          y todos sus datos dependientes, identificandolos por EMAIL.
-- Motivo:  existian usuarios previos con esos emails pero otra cedula,
--          lo que provoca "duplicate key ... usuarios_email_key".
-- Impacto: USUARIOS, USUARIOS_ROLES, ESTUDIANTES, PRACTICAS, VINCULACION,
--          POSTULACIONES_MERITOCRATICAS, BITACORAS, ASISTENCIAS, SESIONES,
--          AUDITORIA, FECHAS_CONVOCATORIA (solo referencias).
-- NO toca al admin ni a ningun otro usuario.

BEGIN;

-- Usuarios objetivo (SOLO los de prueba, por email)
CREATE TEMP TABLE _usuarios_prueba ON COMMIT DROP AS
SELECT id FROM USUARIOS
WHERE email IN (
    'coordinador@sistema.edu.ec',
    'tutor@sistema.edu.ec',
    'estudiante@sistema.edu.ec'
);

-- Estudiantes asociados a esos usuarios
CREATE TEMP TABLE _estudiantes_prueba ON COMMIT DROP AS
SELECT id FROM ESTUDIANTES
WHERE usuario_id IN (SELECT id FROM _usuarios_prueba);

-- 1. Dependencias de los estudiantes de prueba
DELETE FROM POSTULACIONES_MERITOCRATICAS
WHERE estudiante_id IN (SELECT id FROM _estudiantes_prueba);

DELETE FROM BITACORAS
WHERE estudiante_id IN (SELECT id FROM _estudiantes_prueba);

DELETE FROM ASISTENCIAS
WHERE estudiante_id IN (SELECT id FROM _estudiantes_prueba);

-- Practicas del estudiante de prueba (sus evaluaciones/documentos
-- caen en cascada al borrar la practica)
DELETE FROM PRACTICAS
WHERE estudiante_id IN (SELECT id FROM _estudiantes_prueba);

DELETE FROM VINCULACION
WHERE estudiante_id IN (SELECT id FROM _estudiantes_prueba);

-- 2. Referencias donde el usuario de prueba actuo como tutor/encargado:
--    se desvincula sin borrar el expediente (puede ser de un estudiante real)
UPDATE PRACTICAS SET tutor_id = NULL
WHERE tutor_id IN (SELECT id FROM _usuarios_prueba);

UPDATE PRACTICAS SET encargado_id = NULL
WHERE encargado_id IN (SELECT id FROM _usuarios_prueba);

UPDATE VINCULACION SET tutor_id = NULL
WHERE tutor_id IN (SELECT id FROM _usuarios_prueba);

UPDATE VINCULACION SET encargado_id = NULL
WHERE encargado_id IN (SELECT id FROM _usuarios_prueba);

-- 3. Otras referencias a los usuarios de prueba
UPDATE AUDITORIA SET usuario_id = NULL
WHERE usuario_id IN (SELECT id FROM _usuarios_prueba);

UPDATE FECHAS_CONVOCATORIA SET creado_por = NULL
WHERE creado_por IN (SELECT id FROM _usuarios_prueba);

UPDATE USUARIOS_ROLES SET asignado_por = NULL
WHERE asignado_por IN (SELECT id FROM _usuarios_prueba);

-- 4. Registro academico y usuarios
--    (USUARIOS_ROLES y SESIONES caen en cascada al borrar USUARIOS)
DELETE FROM ESTUDIANTES
WHERE id IN (SELECT id FROM _estudiantes_prueba);

DELETE FROM USUARIOS
WHERE id IN (SELECT id FROM _usuarios_prueba);

COMMIT;

-- ============================================================
-- Verificacion: no debe devolver filas
-- ============================================================
SELECT id, cedula, email FROM USUARIOS
WHERE email IN (
    'coordinador@sistema.edu.ec',
    'tutor@sistema.edu.ec',
    'estudiante@sistema.edu.ec'
);
