import { HttpContext, HttpContextToken } from '@angular/common/http';

/** Omite el toast global para una petición controlada en segundo plano. */
export const SKIP_ERROR_TOAST = new HttpContextToken<boolean>(() => false);

/**
 * Identifica peticiones de arranque que validan la sesión almacenada. Un
 * 401/403 en estas llamadas significa que la cuenta o el token ya no sirven.
 */
export const CLEAR_SESSION_ON_AUTH_ERROR = new HttpContextToken<boolean>(() => false);

export function contextoValidacionSesion(): HttpContext {
  return new HttpContext()
    .set(SKIP_ERROR_TOAST, true)
    .set(CLEAR_SESSION_ON_AUTH_ERROR, true);
}
