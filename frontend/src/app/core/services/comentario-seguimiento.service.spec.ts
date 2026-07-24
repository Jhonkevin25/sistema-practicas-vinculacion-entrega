import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ComentarioSeguimientoService } from './comentario-seguimiento.service';

describe('ComentarioSeguimientoService', () => {
  let service: ComentarioSeguimientoService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ComentarioSeguimientoService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(ComentarioSeguimientoService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('consulta comentarios por el expediente sin enviar ids personales del usuario', () => {
    service.getByExpediente('PRACTICAS', 14).subscribe(comentarios => expect(comentarios).toEqual([]));
    const request = http.expectOne('/api/comentarios-seguimiento/practica/14');
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('envía solo expediente, audiencia y mensaje', () => {
    const payload = { practicaId: null, vinculacionId: 8, audiencia: 'COORDINACION' as const, mensaje: 'Consulta al coordinador.' };
    service.create(payload).subscribe();
    const request = http.expectOne('/api/comentarios-seguimiento');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    request.flush({ id: 1, autorId: 2, autor: 'Tutor', autorRol: 'TUTOR', audiencia: 'COORDINACION', mensaje: payload.mensaje, fechaCreacion: '2026-07-21T10:00:00' });
  });
});
