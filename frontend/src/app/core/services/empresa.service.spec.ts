import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { EmpresaService } from './empresa.service';
import { Paginado } from './paginacion';

describe('EmpresaService', () => {
  afterEach(() => localStorage.removeItem('token'));

  it('no reutiliza el listado paginado entre sesiones distintas', () => {
    TestBed.configureTestingModule({
      providers: [EmpresaService, provideHttpClient(), provideHttpClientTesting()]
    });
    const servicio = TestBed.inject(EmpresaService);
    const http = TestBed.inject(HttpTestingController);
    const vacio: Paginado<never> = {
      contenido: [],
      pagina: 0,
      tamano: 20,
      totalElementos: 0,
      totalPaginas: 0
    };

    localStorage.setItem('token', 'token-admin');
    servicio.getPaginado(0, 20).subscribe();
    http.expectOne(req => req.url === '/api/empresas/paginado').flush(vacio);

    servicio.getPaginado(0, 20).subscribe();
    http.expectNone(req => req.url === '/api/empresas/paginado');

    localStorage.setItem('token', 'token-coordinador');
    servicio.getPaginado(0, 20).subscribe();
    http.expectOne(req => req.url === '/api/empresas/paginado').flush(vacio);
    http.verify();
  });
});
