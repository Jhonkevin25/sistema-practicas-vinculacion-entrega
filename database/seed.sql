-- ============================================================
-- SEED.SQL — Datos iniciales del sistema
-- Ejecutar DESPUÉS de schema.sql
-- ============================================================

-- ============================================================
-- ROLES
-- ============================================================
INSERT INTO ROLES (codigo, nombre, activo) VALUES
    ('ADMIN',        'Administrador',         TRUE),
    ('COORDINADOR',  'Coordinador',           TRUE),
    ('TUTOR',        'Tutor',                 TRUE),
    ('ESTUDIANTE',   'Estudiante',            TRUE),
    ('EMPRESA',      'Empresa/Fundación',     TRUE)
ON CONFLICT (codigo) DO NOTHING;

-- ============================================================
-- MÓDULOS
-- ============================================================
INSERT INTO MODULOS (codigo, nombre, ruta_base, icono, orden) VALUES
    ('DASHBOARD',   'Dashboard',              '/dashboard',           'home',         1),
    ('USUARIOS',    'Gestión de Usuarios',    '/usuarios',            'users',        2),
    ('ROLES',       'Roles y Permisos',       '/roles',               'shield',       3),
    ('EMPRESAS',    'Empresas',               '/empresas',            'building',     4),
    ('FUNDACIONES', 'Fundaciones',            '/fundaciones',         'heart',        5),
    ('PRACTICAS',   'Prácticas',              '/practicas',           'briefcase',    6),
    ('VINCULACION', 'Vinculación',            '/vinculacion',         'link',         7),
    ('ESTUDIANTES', 'Estudiantes',            '/estudiantes',         'graduation-cap', 8),
    ('REPORTES',    'Reportes',               '/reportes',            'chart-bar',    9),
    ('AUDITORIA',   'Auditoría',              '/auditoria',           'clipboard',    10)
ON CONFLICT (codigo) DO NOTHING;

-- ============================================================
-- PERMISOS
-- ============================================================
INSERT INTO PERMISOS (codigo, nombre) VALUES
    ('CREATE', 'Crear'),
    ('READ',   'Leer'),
    ('UPDATE', 'Actualizar'),
    ('DELETE', 'Eliminar')
ON CONFLICT DO NOTHING;

-- ============================================================
-- USUARIO ADMINISTRADOR INICIAL
-- password: Admin2026# (hash bcrypt)
-- IMPORTANTE: cambiar la contraseña en el primer login
-- ============================================================
INSERT INTO USUARIOS (cedula, nombre, apellido, email, password_hash, activo, primer_login)
VALUES (
    '0000000001',
    'Administrador',
    'Sistema',
    'admin@sistema.edu.ec',
    '$2b$10$rQZ9uAFGJ2K8vX3mN5pL4OyB1wE6sT0dC7hM9iU2jV4kW8xP6nR3q',
    TRUE,
    TRUE
)
ON CONFLICT (cedula) DO NOTHING;

-- ============================================================
-- ASIGNAR ROL ADMIN AL USUARIO ADMINISTRADOR
-- ============================================================
INSERT INTO USUARIOS_ROLES (usuario_id, rol_id, asignado_por)
SELECT u.id, r.id, u.id
FROM USUARIOS u, ROLES r
WHERE u.cedula = '0000000001'
  AND r.codigo = 'ADMIN'
ON CONFLICT DO NOTHING;

-- ============================================================
-- PERMISOS COMPLETOS PARA EL ROL ADMIN (todos los módulos)
-- ============================================================
INSERT INTO ROLES_MODULOS_PERMISOS (rol_id, modulo_id, permiso_id)
SELECT r.id, m.id, p.id
FROM ROLES r
CROSS JOIN MODULOS m
CROSS JOIN PERMISOS p
WHERE r.codigo = 'ADMIN'
ON CONFLICT DO NOTHING;

-- ============================================================
-- PERMISOS PARA ROL COORDINADOR
-- ============================================================
INSERT INTO ROLES_MODULOS_PERMISOS (rol_id, modulo_id, permiso_id)
SELECT r.id, m.id, p.id
FROM ROLES r
CROSS JOIN MODULOS m
CROSS JOIN PERMISOS p
WHERE r.codigo = 'COORDINADOR'
  AND m.codigo IN ('DASHBOARD','PRACTICAS','VINCULACION','ESTUDIANTES','EMPRESAS','FUNDACIONES','REPORTES')
  AND p.codigo IN ('CREATE','READ','UPDATE')
ON CONFLICT DO NOTHING;

-- ============================================================
-- PERMISOS PARA ROL TUTOR
-- ============================================================
INSERT INTO ROLES_MODULOS_PERMISOS (rol_id, modulo_id, permiso_id)
SELECT r.id, m.id, p.id
FROM ROLES r
CROSS JOIN MODULOS m
CROSS JOIN PERMISOS p
WHERE r.codigo = 'TUTOR'
  AND m.codigo IN ('DASHBOARD','PRACTICAS','VINCULACION','ESTUDIANTES')
  AND p.codigo IN ('READ','UPDATE')
ON CONFLICT DO NOTHING;

-- ============================================================
-- PERMISOS PARA ROL ESTUDIANTE
-- ============================================================
INSERT INTO ROLES_MODULOS_PERMISOS (rol_id, modulo_id, permiso_id)
SELECT r.id, m.id, p.id
FROM ROLES r
CROSS JOIN MODULOS m
CROSS JOIN PERMISOS p
WHERE r.codigo = 'ESTUDIANTE'
  AND m.codigo IN ('DASHBOARD','PRACTICAS','VINCULACION')
  AND p.codigo = 'READ'
ON CONFLICT DO NOTHING;

