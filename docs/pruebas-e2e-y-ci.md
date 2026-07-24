# Pruebas E2E y CI del MVP

La Fase 37 incorpora Playwright para comprobar el acceso público y la navegación
visible por rol. Las credenciales no se guardan en archivos del repositorio.

## 1. Preparación local

Desde `frontend`:

```powershell
npm ci
npx playwright install chromium
```

Por defecto, Playwright usa `http://localhost:4200` e inicia el backend y el
frontend si no están activos. En desarrollo reutiliza servidores existentes. Para
probar un despliegue ya publicado, define `E2E_BASE_URL`; en ese modo no inicia
servidores locales.

## 2. Comandos

```powershell
npm run e2e:public
npm run e2e
npm run e2e:roles
npm run e2e:report
```

- `e2e:public`: acceso institucional, redirección de ruta protegida, respuesta
  genérica ante credenciales inválidas y recuperación sin enumerar correos.
- `e2e`: ejecuta todos los casos. Los casos por rol se omiten individualmente si
  faltan sus variables.
- `e2e:roles`: limita la ejecución a ADMIN, COORDINADOR, TUTOR y ESTUDIANTE.
- `e2e:report`: abre el último informe HTML local.

La suite usa un solo worker para no competir por las conexiones del pooler. Ante
un fallo conserva captura, video y trace en `frontend/test-results/`; esos
artefactos están ignorados por Git.

## 3. Cuentas de prueba por rol

Las cuentas deben estar activas y haber completado el cambio de contraseña del
primer ingreso. Define solo las que vayas a probar:

```powershell
$env:E2E_ADMIN_EMAIL='correo-admin-de-prueba'
$env:E2E_ADMIN_PASSWORD='clave-de-prueba'
$env:E2E_COORDINADOR_EMAIL='correo-coordinador-de-prueba'
$env:E2E_COORDINADOR_PASSWORD='clave-de-prueba'
$env:E2E_TUTOR_EMAIL='correo-tutor-de-prueba'
$env:E2E_TUTOR_PASSWORD='clave-de-prueba'
$env:E2E_ESTUDIANTE_EMAIL='correo-estudiante-de-prueba'
$env:E2E_ESTUDIANTE_PASSWORD='clave-de-prueba'
npm run e2e:roles
```

No pegues valores reales en este documento, `package.json`, el workflow ni los
archivos `*.spec.ts`. Para limpiar la sesión de PowerShell, cierra la terminal o
elimina las variables del entorno.

## 4. GitHub Actions

El workflow `.github/workflows/validacion-mvp.yml` se ejecuta en cada `push`,
`pull_request` y lanzamiento manual:

1. `calidad-sin-secretos` compila backend y pruebas, ejecuta las pruebas unitarias
   Angular y genera el build de producción. Este trabajo no accede a Supabase.
2. `integracion-con-secretos` ejecuta las pruebas backend contra Supabase y la
   suite Playwright únicamente si están disponibles `DB_URL`, `DB_USERNAME`,
   `DB_PASSWORD` y `APP_JWT_SECRET`.
3. Cada prueba por rol se habilita al agregar su pareja `E2E_*_EMAIL` y
   `E2E_*_PASSWORD`. Si una pareja falta, solo ese caso queda omitido.

Secretos admitidos en GitHub (`Settings > Secrets and variables > Actions`):

```text
DB_URL
DB_USERNAME
DB_PASSWORD
APP_JWT_SECRET
SUPABASE_URL
SUPABASE_SERVICE_ROLE_KEY
E2E_ADMIN_EMAIL
E2E_ADMIN_PASSWORD
E2E_COORDINADOR_EMAIL
E2E_COORDINADOR_PASSWORD
E2E_TUTOR_EMAIL
E2E_TUTOR_PASSWORD
E2E_ESTUDIANTE_EMAIL
E2E_ESTUDIANTE_PASSWORD
```

Los secretos de Storage son opcionales para estos escenarios, pero permiten que
el contexto use la misma configuración del entorno de integración. El correo se
mantiene desactivado en CI. En caso de fallo, GitHub conserva las evidencias de
Playwright durante siete días.
