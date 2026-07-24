# Fase 9 — Guion de pruebas manuales por rol (sistema completo)

Actualiza y reemplaza al guion de Fase 4: cubre las funciones conectadas en
las Fases 5-6 (documentos, favoritos, auditoría, alcance del coordinador y
reglas de negocio en el backend).

Prerequisitos:
- Backend corriendo (`./gradlew.bat bootRun`) y frontend (`npm start`).
- Usuarios de prueba de `database/fase4_usuarios_prueba.sql` ya en Supabase.
- Para partir de cero: ejecutar `database/fase4_reset_estudiante.sql`
  (limpia expediente, documentos y favoritos del estudiante de prueba).

Credenciales:

| Rol | Email | Contraseña |
|---|---|---|
| ADMIN | admin@sistema.edu.ec | (la tuya) |
| COORDINADOR | coordinador@sistema.edu.ec | Coordinador2026* |
| TUTOR | tutor@sistema.edu.ec | Tutor2026* |
| ESTUDIANTE | estudiante@sistema.edu.ec | Estudiante2026* |

La parte automatizada de esta fase ya corre sola:
- Backend: `./gradlew.bat test` — matriz de autorización por rol (13 pruebas).
- Frontend: `npm test` — lógica académica y loaders por rol (15 pruebas).

## 1. COORDINADOR — preparación
- [ ] Configura fechas de convocatoria; persisten tras F5.
- [ ] Publica una vacante de Práctica I para Ingeniería en Software; persiste.
- [ ] Cambia el perfil del simulador (p. ej. a NEG): las listas de estudiantes,
      prácticas y vacantes se vacían (el filtro ahora es del servidor).
      Tras F5 el perfil se mantiene (guardado en BD). Regresa a TEC.

## 2. ESTUDIANTE — expediente y postulación
- [ ] Sube CV, carta y cédula; tras F5 siguen marcados (BD, no localStorage).
- [ ] Marca una vacante como favorita; tras F5 y en otro navegador persiste.
- [ ] Postula con preferencias 1 y 2 dentro de fechas → éxito.
- [ ] Intenta postular de nuevo → bloqueado ("postulación en proceso").
- [ ] (API/negativo) Sin documentos completos el backend rechaza la
      postulación aunque se fuerce la petición.

## 3. COORDINADOR — meritocracia y auditoría
- [ ] Ejecuta el algoritmo → postulación pasa a Procesado.
- [ ] Ajuste manual con justificación → aparece en la Bitácora de Auditoría
      con tu nombre y fecha; persiste tras F5 y desde otro navegador.
- [ ] Consolida → se crea la práctica oficial (en_curso, sin duplicados:
      intentar consolidar dos veces la misma postulación falla con mensaje).
- [ ] Asigna tutor desde la columna "Tutor Asignado" de Asignaciones Activas.

## 4. TUTOR — seguimiento y calificación
- [ ] Solo ve a sus estudiantes asignados.
- [ ] Aprueba bitácoras; las horas del estudiante se actualizan.
- [ ] Califica P1; intentar calificar P2 sin cerrar P1 → bloqueado
      (secuencialidad de parciales).
- [ ] Notas fuera de 0-10 → rechazadas.

## 5. ESTUDIANTE — cierre
- [ ] Overview muestra etapa, horas, tutor y notas reales del parcial.
- [ ] Completa la encuesta de pertinencia (Prácticas → Mi Avance →
      Mis Calificaciones); el estado pasa a COMPLETADA también en Overview.

## 6. Reglas de estado (cualquier rol gestor, por API o pantalla)
- [ ] Una práctica completada no puede volver a en_curso ni a pendiente
      (400 "Transición de estado no permitida").
- [ ] Una postulación Aprobada no puede volver a Pendiente.
- [ ] Horas requeridas en 0 → rechazado también al editar.

## 7. Accesos indebidos (URL forzada, sin pantalla rota)
- [ ] ESTUDIANTE a /dashboard/usuarios → acceso restringido.
- [ ] TUTOR a /dashboard/usuarios → acceso restringido.
- [ ] EMPRESA (si existiera un usuario) no accede a ningún endpoint del API.

## 8. Persistencia entre navegadores
- [ ] Mismo usuario en ventana privada: documentos, favoritos, auditoría,
      alcance del coordinador y notas idénticos (nada vive en localStorage).

## 9. ngrok
- [ ] Con la URL de ngrok en `APP_CORS_ALLOWED_ORIGINS`, login + una
      operación CRUD funcionan desde la URL compartida.
