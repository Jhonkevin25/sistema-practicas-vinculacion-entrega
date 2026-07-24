import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';
import { CLEAR_SESSION_ON_AUTH_ERROR, SKIP_ERROR_TOAST } from './http-context-tokens';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const toastService = inject(ToastService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      let userFriendlyMessage = 'Ha ocurrido un error inesperado. Por favor, intenta de nuevo.';
      const debeLimpiarSesion = req.context.get(CLEAR_SESSION_ON_AUTH_ERROR)
        && (error.status === 401 || error.status === 403);

      if (debeLimpiarSesion) {
        authService.logout();
      }

      if (error.status === 400) {
        // El backend responde { error: "motivo" } en las validaciones de negocio:
        // mostrar el motivo real, no un mensaje genérico que oculte la causa.
        userFriendlyMessage = error.error?.error || 'Los datos enviados no son válidos. Revisa el formulario e intenta de nuevo.';
      } else if (error.status === 401) {
        userFriendlyMessage = 'Sesión expirada o no autorizada. Redireccionando al inicio de sesión.';
        if (!debeLimpiarSesion) authService.logout();
      } else if (error.status === 403) {
        userFriendlyMessage = 'Acceso Restringido: No tienes los permisos necesarios para realizar esta acción académica.';
      } else if (error.status === 428) {
        userFriendlyMessage = error.error?.error || 'Debes cambiar tu contraseña antes de continuar.';
        authService.marcarPrimerLoginPendiente();
        router.navigate(['/cambiar-password']);
      } else if (error.status === 500) {
        userFriendlyMessage = 'Error del Servidor (500): Ocurrió una anomalía interna en el servidor de UNIBE.';
      } else if (error.status === 0) {
        userFriendlyMessage = 'Sin Conexión: No se pudo establecer comunicación con el servidor. Verifica tu conexión de red.';
      }

      if (!debeLimpiarSesion) {
        console.error(`[ISO 25010 Error Audit] Status: ${error.status} | URL: ${error.url} | Message: ${error.message}`);
      }
      if (!req.context.get(SKIP_ERROR_TOAST)) {
        toastService.error(userFriendlyMessage);
      }
      return throwError(() => error);
    })
  );
};
