import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { describe, expect, it, vi } from 'vitest';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';
import { errorInterceptor } from './error.interceptor';
import { CLEAR_SESSION_ON_AUTH_ERROR, SKIP_ERROR_TOAST } from './http-context-tokens';

describe('errorInterceptor', () => {
  function preparar() {
    const auth = { logout: vi.fn(), marcarPrimerLoginPendiente: vi.fn() };
    const toast = { error: vi.fn() };
    const router = { navigate: vi.fn() };
    vi.spyOn(console, 'error').mockImplementation(() => undefined);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
        { provide: ToastService, useValue: toast },
        { provide: Router, useValue: router }
      ]
    });

    return {
      auth,
      toast,
      http: TestBed.inject(HttpClient),
      httpMock: TestBed.inject(HttpTestingController)
    };
  }

  it('limpia sin toast una sesión almacenada que el backend rechaza', () => {
    const { auth, toast, http, httpMock } = preparar();
    const context = new HttpContext()
      .set(SKIP_ERROR_TOAST, true)
      .set(CLEAR_SESSION_ON_AUTH_ERROR, true);

    http.get('/api/configuracion/periodos/activo', { context }).subscribe({ error: () => undefined });
    httpMock.expectOne('/api/configuracion/periodos/activo')
      .flush({}, { status: 403, statusText: 'Forbidden' });

    expect(auth.logout).toHaveBeenCalledOnce();
    expect(toast.error).not.toHaveBeenCalled();
    httpMock.verify();
  });

  it('mantiene el aviso y la sesión ante un 403 funcional', () => {
    const { auth, toast, http, httpMock } = preparar();

    http.post('/api/recurso-administrativo', {}).subscribe({ error: () => undefined });
    httpMock.expectOne('/api/recurso-administrativo')
      .flush({}, { status: 403, statusText: 'Forbidden' });

    expect(auth.logout).not.toHaveBeenCalled();
    expect(toast.error).toHaveBeenCalledOnce();
    httpMock.verify();
  });
});
