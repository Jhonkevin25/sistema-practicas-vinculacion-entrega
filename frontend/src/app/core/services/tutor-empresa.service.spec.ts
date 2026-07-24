import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TutorEmpresaService } from './tutor-empresa.service';

describe('TutorEmpresaService', () => {
  let service: TutorEmpresaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [TutorEmpresaService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(TutorEmpresaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('consulta tutores compatibles por empresa y carrera', () => {
    service.getCompatibles(7, 'Ingeniería en Software').subscribe();

    const request = http.expectOne(req =>
      req.url === '/api/tutores-empresa/compatibles'
      && req.params.get('empresaId') === '7'
      && req.params.get('carrera') === 'Ingeniería en Software');
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('crea un vínculo con varias carreras', () => {
    const payload = {
      tutorId: 11,
      empresaId: 7,
      cargo: 'Supervisor de área',
      activo: true,
      carreraIds: [2, 5]
    };

    service.create(payload).subscribe();

    const request = http.expectOne('/api/tutores-empresa');
    expect(request.request.method).toBe('POST');
    expect(request.request.body.carreraIds).toEqual([2, 5]);
    request.flush({});
  });
});
