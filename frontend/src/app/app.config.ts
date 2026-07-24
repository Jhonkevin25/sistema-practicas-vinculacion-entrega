import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { errorInterceptor } from './core/interceptors/error.interceptor';
import { AuthService } from './core/services/auth.service';
import { PeriodoAcademicoService } from './core/services/periodo-academico.service';
import { of, switchMap } from 'rxjs';

import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
    // Con sesion activa (F5), validar primero la cuenta y resolver el alcance.
    // Solo después se consulta el periodo; así una sesión eliminada no dispara
    // dos peticiones protegidas en paralelo durante el arranque.
    provideAppInitializer(() => {
      const auth = inject(AuthService);
      if (!auth.isLoggedIn() || auth.requiresPasswordChange()) return;
      const periodo = inject(PeriodoAcademicoService);
      return auth.cargarAlcance().pipe(
        switchMap(() => auth.isLoggedIn() ? periodo.cargarActivo() : of(null))
      );
    })
  ],
};
