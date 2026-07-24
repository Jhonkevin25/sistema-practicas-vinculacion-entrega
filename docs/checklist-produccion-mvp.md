# Checklist de preparación para producción del MVP

Este documento no despliega el sistema. Define el punto de salida para evitar
publicar un build correcto con configuración o datos incompletos.

## 1. Decisiones externas necesarias

- Proveedor y URL pública del backend.
- Hosting y dominio del frontend.
- Dominio HTTPS definitivo.
- SMTP institucional o proveedor transaccional definitivo.
- Responsable de Supabase y política de copias de seguridad.
- Contrato de API universitaria, si se habilitará en la primera versión.

Mailtrap es correcto para demostración, pero no para enviar credenciales o
avisos a estudiantes reales.

## 2. Supabase

- Aplicar todos los scripts confirmados hasta `fase36_endurecimiento_login.sql` (`fase29`, `fase30`, `fase31` y `fase37` no modifican el esquema).
- Verificar que `BITACORAS.parcial` tenga `is_nullable = NO`.
- Confirmar que el bucket privado se llame exactamente `documentos`.
- Mantener la service role únicamente en el backend.
- Usar el pooler PostgreSQL; no el host directo IPv6.
- Crear una copia de seguridad antes de importar datos institucionales reales.
- Retirar o anonimizar datos de prueba antes de abrir el sistema.

### Política de respaldo (mínimo exigible)

- Supabase realiza respaldos automáticos diarios en los planes de pago; en el
  plan gratuito NO hay respaldo automático garantizado — verificar el plan
  contratado antes de la salida.
- Independiente del plan: exportar un respaldo manual (`pg_dump` vía pooler o
  el export del panel de Supabase) ANTES de cada importación institucional
  masiva y al cierre de cada periodo académico, y almacenarlo cifrado fuera de
  Supabase (los datos incluyen información personal de estudiantes).
- Probar al menos una vez la restauración del respaldo en un proyecto Supabase
  de prueba: un respaldo que nunca se restauró no es un respaldo.

## 3. Secretos y configuración

Configurar en el host, nunca en Git:

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`;
- `APP_JWT_SECRET` largo, aleatorio y exclusivo de producción;
- parámetros `APP_AUTH_LOGIN_*` si se requieren valores distintos a los defaults seguros;
- `APP_AUTH_TRUST_FORWARDED_FOR=true` únicamente detrás de un proxy confiable que reemplace ese encabezado;
- `APP_CORS_ALLOWED_ORIGINS` con el dominio exacto del frontend;
- `APP_MAIL_ENABLED`, remitente y credenciales SMTP;
- `APP_MAIL_BASE_URL` con la URL HTTPS del frontend;
- `SUPABASE_URL`, service role, bucket y duración de URL firmada.

Revisar que `.env.properties`, archivos de credenciales, tokens y exportaciones
de datos no aparezcan en `git status` ni en el historial que se publicará.

## 4. Build y publicación

### Backend

1. Usar Java 17.
2. Ejecutar `./gradlew.bat test` sin paralelizar.
3. Ejecutar `./gradlew.bat build`.
4. Publicar el JAR generado en `backend/sistema-practicas/build/libs/`.
5. Ejecutarlo detrás de HTTPS y limitar el acceso administrativo al host.

### Frontend

1. Ejecutar `npm test -- --watch=false`.
2. Ejecutar `npm run e2e:public` con backend y frontend disponibles.
3. Configurar cuentas desechables `E2E_*` y ejecutar `npm run e2e:roles` antes de la salida final.
4. Ejecutar `npm run build`.
5. Publicar `frontend/dist/frontend-app/` como sitio estático.
6. Enrutar `/api` al backend o configurar CORS con el origen definitivo.
7. Comprobar recarga directa de rutas Angular sin respuesta 404 del hosting.

### Integración continua

1. Confirmar que `.github/workflows/validacion-mvp.yml` pasa en la rama que se publicará.
2. Guardar `DB_*`, `APP_JWT_SECRET`, Storage y cuentas `E2E_*` únicamente como secretos de GitHub.
3. Verificar que los pasos de backend real y E2E de `integracion-con-secretos` no figuren como omitidos antes de producción.
4. Descargar las evidencias Playwright si falla un escenario y no publicar esos artefactos fuera del equipo autorizado.

## 5. Prueba de humo por rol

- ADMIN configura alcance y proceso de coordinadores, requisitos y calendario.
- COORDINADOR PRACTICAS no ve ni modifica recursos de VINCULACION.
- COORDINADOR VINCULACION no ve ni modifica recursos de PRACTICAS.
- TUTOR solo consulta sus tutorías, revisa bitácoras y registra su nota.
- ESTUDIANTE solo accede a su expediente, documentos, postulaciones y encuestas.
- Una asignación descuenta un único cupo y nunca deja valores negativos.
- Un documento privado solo genera URL firmada para su dueño o revisor autorizado.
- SMTP fallido no produce error 500 en el flujo académico.
- Cinco contraseñas incorrectas bloquean temporalmente la cuenta y el login no revela si el correo existe.
- Cerrar un expediente exige horas, parciales, encuestas y documentos completos.
- Reimportar el mismo archivo institucional no duplica registros.
- La bandeja unificada muestra únicamente procesos y carreras dentro del alcance del coordinador.
- En móvil, el menú lateral abre y cierra sin reducir ni ocultar el contenido principal.

## 6. Salida y reversión

- Registrar la versión desplegada y el commit exacto.
- Conservar el artefacto anterior para reversión.
- No ejecutar scripts de limpieza o reset en producción.
- Si falla una migración, detener el despliegue; no modificar manualmente la
  entidad JPA para saltarse `ddl-auto=validate`.
- Revisar logs de autenticación, correo, Storage e importaciones durante la
  primera jornada de uso.

## 7. Condición de salida

El MVP puede abrirse a usuarios reales cuando el SQL esté aplicado, los builds
estén verdes, los secretos sean exclusivos de producción, el flujo completo se
haya probado por rol sobre el dominio HTTPS y exista una copia de seguridad.
