# Contrato pendiente para la API institucional

## Estado actual

El MVP ya tiene una única rutina transaccional de identidad académica en
`ImportacionInstitucionalComponent`. Esa rutina:

- crea o actualiza por `external_id`;
- usa el correo institucional como segunda clave de enlace;
- enlaza cuentas manuales existentes sin duplicarlas;
- actualiza matrícula, carrera, semestre y estado activo;
- persiste promedios verificados del semestre correspondiente;
- crea usuarios nuevos con cambio obligatorio de contraseña;
- registra auditoría y evita duplicados al reprocesar datos.

El componente distingue `CSV_UNIVERSIDAD` y `API_UNIVERSIDAD`. Por tanto, un
cliente HTTP futuro debe transformar la respuesta externa a
`FilaEstudianteInstitucional` y `FilaNotaInstitucional`, y reutilizar la misma
rutina. No se necesita modificar el esquema de la base de datos.

La conexión HTTP real **no se implementa todavía** porque el proyecto no tiene
el contrato oficial de la universidad. Crear una URL, autenticación o nombres
de campos supuestos podría enlazar cuentas equivocadas o exponer información
académica.

## Información que debe entregar la universidad

1. URL base y ambiente de pruebas.
2. Autenticación: OAuth2 client credentials, API key, mTLS u otro mecanismo.
3. Endpoints de estudiantes y notas.
4. Esquema JSON oficial y tipos de datos.
5. Paginación, filtros por periodo y marca de última actualización.
6. Límites de consumo y códigos de error.
7. Política de reintentos y disponibilidad.
8. IP allowlist, certificados o VPN, si aplica.
9. Dominio oficial permitido para correos estudiantiles.
10. Responsable institucional para resolver conflictos de identidad.

## Datos mínimos normalizados

### Estudiante

| Campo interno | Obligatorio | Uso |
|---|---:|---|
| `externalId` | Sí | Identificador estable de la universidad |
| `cedula` | Sí | Validación de identidad y duplicados |
| `emailInstitucional` | Sí | Cuenta y canal de notificación |
| `nombre`, `apellido` | Sí | Perfil institucional |
| `matricula` | Recomendado | Expediente académico; usa `externalId` si falta |
| `carrera` | Sí | Alcance y compatibilidad académica |
| `semestre` | Sí | Etapa y promedio anterior |
| `periodoAcademico` | Recomendado | Contexto de matrícula |
| `matriculaActiva` | Sí | Activación o desactivación de acceso |

### Nota académica

| Campo interno | Obligatorio | Uso |
|---|---:|---|
| `externalId` o `emailInstitucional` | Sí | Resolución del estudiante |
| `periodoAcademico` | Sí | Idempotencia de la nota |
| `semestre` | Recomendado | Se infiere como anterior solo si es seguro |
| `promedio` | Sí | Score meritocrático, rango 0–10 |

## Ejemplo ilustrativo, no contractual

```json
{
  "estudiantes": [
    {
      "externalId": "U-001",
      "cedula": "1711111111",
      "emailInstitucional": "estudiante@universidad.edu.ec",
      "nombre": "Ana",
      "apellido": "Perez",
      "matricula": "MAT-001",
      "carrera": "Ingenieria en Software",
      "semestre": 5,
      "periodoAcademico": "2026-1",
      "matriculaActiva": true
    }
  ]
}
```

## Reglas para implementar el cliente HTTP

- Debe ser un `@Component` compartido, no un `@Service` nuevo.
- Sus credenciales se guardan solo en `.env.properties` o variables del host.
- La sincronización será solo ADMIN y entrará en `SecurityConfig` y
  `SecurityMatrixTests`.
- Cada página recibida se transforma a los records internos y se procesa con
  `FuenteInstitucional.API_UNIVERSIDAD`.
- Los fallos por fila se registran sin ocultar las filas correctas; un fallo de
  autenticación o contrato detiene la sincronización completa.
- Nunca se confía en carrera, correo o ID enviados por el frontend.
- La reejecución del mismo periodo no debe crear usuarios, expedientes ni notas
  duplicadas.

## Criterio de aceptación de la integración real

1. Ambiente sandbox de la universidad disponible.
2. Contrato y autenticación documentados.
3. Primera sincronización con datos anonimizados.
4. Repetir la sincronización produce cero duplicados.
5. Conflictos de `external_id`, correo, cédula o matrícula quedan reportados.
6. Los registros API quedan con fuente `API_UNIVERSIDAD`.
7. Auditoría, correo de bienvenida y cambio obligatorio de contraseña funcionan.
8. Pruebas backend y guion manual por rol quedan verdes.
