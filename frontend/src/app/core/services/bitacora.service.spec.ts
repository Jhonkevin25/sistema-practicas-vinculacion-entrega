import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Bitacora, BitacoraService, correccionBitacoraPendiente } from './bitacora.service';

describe('BitacoraService', () => {
  it('reenvía solo los campos editables de la bitácora observada', () => {
    TestBed.configureTestingModule({
      providers: [BitacoraService, provideHttpClient(), provideHttpClientTesting()]
    });
    const servicio = TestBed.inject(BitacoraService);
    const http = TestBed.inject(HttpTestingController);
    const correccion = {
      fecha: '2026-07-20',
      actividad: 'Actividad corregida',
      horas: 4,
      observaciones: 'Evidencia ampliada'
    };

    servicio.reenviar(15, correccion).subscribe();

    const request = http.expectOne('/api/bitacoras/15/reenviar');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(correccion);
    request.flush({ id: 15, ...correccion, estado: 'pendiente' });
    http.verify();
  });
});

describe('estado de corrección de bitácora', () => {
  const estudiante = {} as Bitacora['estudiante'];
  const observada: Bitacora = {
    id: 10,
    estudiante,
    parcial: 1,
    fecha: '2026-07-18',
    actividad: 'Actividad observada',
    horas: 4,
    estado: 'requiere_correccion'
  };

  it('mantiene disponible la corrección mientras no haya aprobación posterior', () => {
    expect(correccionBitacoraPendiente(observada, [observada])).toBe(true);
  });

  it('reconoce una corrección histórica resuelta por una entrega aprobada posterior', () => {
    const aprobada: Bitacora = { ...observada, id: 11, estado: 'aprobada' };
    expect(correccionBitacoraPendiente(observada, [observada, aprobada])).toBe(false);
  });
});
