import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CorreoCola, CorreoService } from './correo.service';

describe('CorreoService', () => {
  it('consume el endpoint POST de reintento', () => {
    TestBed.configureTestingModule({
      providers: [CorreoService, provideHttpClient(), provideHttpClientTesting()]
    });
    const servicio = TestBed.inject(CorreoService);
    const http = TestBed.inject(HttpTestingController);
    const respuesta: CorreoCola = {
      id: 12,
      destinatario: 'coordinacion@unibe.edu.ec',
      asunto: 'Aviso',
      cuerpoHtml: '<p>Aviso</p>',
      estado: 'PENDIENTE',
      intentos: 0,
      ultimoError: null,
      fechaCreacion: '2026-07-18T10:00:00',
      fechaActualizacion: '2026-07-18T10:06:00'
    };

    servicio.reintentar(12).subscribe(correo => expect(correo).toEqual(respuesta));

    const peticion = http.expectOne('/api/correos/12/reintentar');
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({});
    peticion.flush(respuesta);
    http.verify();
  });
});
