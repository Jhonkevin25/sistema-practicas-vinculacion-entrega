# Fase 4 — Guion de pruebas manuales del flujo MVP por rol

Prerequisito: ejecutar `database/fase4_usuarios_prueba.sql` en Supabase SQL Editor
(crea los usuarios de prueba y la convocatoria 2026-2 vigente).

Credenciales de prueba:

| Rol | Email | Contraseña |
|---|---|---|
| ADMIN | admin@sistema.edu.ec | (la tuya; el SQL trae un UPDATE opcional para restablecerla) |
| COORDINADOR | coordinador@sistema.edu.ec | Coordinador2026* |
| TUTOR | tutor@sistema.edu.ec | Tutor2026* |
| ESTUDIANTE | estudiante@sistema.edu.ec | Estudiante2026* |

Marca cada casilla al validar. Si algo falla, anota pantalla + acción + mensaje.

## 1. ADMIN
- [ ] Login correcto y redirección al dashboard.
- [ ] Ve todos los módulos del menú.
- [ ] Usuarios: crear un usuario nuevo, editarlo, desactivarlo, reactivarlo.
- [ ] Estudiantes: crear/editar (vinculando al usuario creado).
- [ ] Empresas: crear/editar/activar-desactivar.
- [ ] Fundaciones y proyectos: crear/editar.
- [ ] Prácticas y vinculación: ver listados globales.
- [ ] Refrescar el navegador (F5): la sesión y los datos persisten.

## 2. COORDINADOR
- [ ] Login correcto.
- [ ] NO ve el módulo de usuarios en el menú (o al forzar la URL recibe mensaje de acceso restringido, no pantalla rota).
- [ ] Estudiantes: puede listar y editar.
- [ ] Configura fechas de convocatoria y quedan guardadas (verificar tras F5).
- [ ] Publica una vacante de práctica (tras Fase 5) y persiste.
- [ ] Procesa una postulación pendiente (tras Fase 5).
- [ ] Asigna tutor + empresa a un estudiante; la práctica aparece en el listado.

## 3. TUTOR
- [ ] Login correcto.
- [ ] Solo ve prácticas/vinculaciones donde él es tutor (con el usuario tutor de prueba recién creado la lista debe estar VACÍA hasta que el coordinador le asigne un estudiante).
- [ ] Tras asignación del coordinador: ve al estudiante asignado.
- [ ] Puede ver bitácoras y asistencias del asignado (tras Fase 5).
- [ ] Puede registrar horas/seguimiento (actualizar práctica).
- [ ] Intentar URL de usuarios → acceso restringido, sin pantalla rota.

## 4. ESTUDIANTE
- [ ] Login correcto; ve su carrera en el panel.
- [ ] Overview muestra su etapa (nota: hasta la Fase 5 el avance aún es simulado).
- [ ] Ve vacantes/proyectos disponibles (tras Fase 5: reales, desde la API).
- [ ] Postula dentro de fechas de convocatoria; fuera de fechas se bloquea.
- [ ] Registra bitácora y asistencia; tras F5 siguen ahí (tras Fase 5).
- [ ] NO ve datos de otros estudiantes en ninguna pantalla.
- [ ] Intentar URL de usuarios/estudiantes → acceso restringido, sin pantalla rota.

## 5. Persistencia entre equipos/navegadores
- [ ] Iniciar sesión con el mismo usuario en otro navegador (o ventana privada): los mismos datos aparecen (nada depende del localStorage del primer navegador). *Se cumplirá plenamente al cerrar la Fase 5.*

## 6. ngrok
- [ ] Con la URL de ngrok en `APP_CORS_ALLOWED_ORIGINS` (`.env.properties`), el sistema funciona desde la URL compartida (login + una operación CRUD).
