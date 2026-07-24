import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ExpedienteService } from './expediente.service';
import { Practica } from './practica.service';
import { Vinculacion } from './vinculacion.service';
import { Usuario } from './usuario.service';

const practica = (estado: string) => ({ estado } as Practica);
const vinculacion = (estado: string) => ({ estado } as Vinculacion);

describe('ExpedienteService', () => {
  let service: ExpedienteService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(ExpedienteService);
  });

  it('periodoActual tiene formato AAAA-semestre', () => {
    expect(service.periodoActual()).toMatch(/^\d{4}-[12]$/);
  });

  describe('etapaDe (Vinculación -> Práctica I -> Práctica II)', () => {
    it('sin vinculación completada la etapa es Vinculación', () => {
      expect(service.etapaDe([], [])).toBe('Vinculación');
      expect(service.etapaDe([], [vinculacion('en_curso')])).toBe('Vinculación');
    });

    it('con vinculación completada y sin prácticas completadas es Práctica I', () => {
      expect(service.etapaDe([], [vinculacion('completado')])).toBe('Práctica I');
      expect(service.etapaDe([practica('en_curso')], [vinculacion('completado')])).toBe('Práctica I');
    });

    it('con una práctica completada es Práctica II', () => {
      expect(service.etapaDe([practica('completado')], [vinculacion('completado')])).toBe('Práctica II');
    });

    it('con dos prácticas completadas el proceso está COMPLETADO', () => {
      expect(service.etapaDe(
        [practica('completado'), practica('completado')],
        [vinculacion('completado')]
      )).toBe('COMPLETADO');
    });
  });

  describe('hayActividadActiva (regla de exclusividad)', () => {
    it('detecta prácticas o vinculaciones pendientes o en curso', () => {
      expect(service.hayActividadActiva([practica('en_curso')], [])).toBe(true);
      expect(service.hayActividadActiva([], [vinculacion('pendiente')])).toBe(true);
    });

    it('los procesos completados no cuentan como actividad activa', () => {
      expect(service.hayActividadActiva([practica('completado')], [vinculacion('completado')])).toBe(false);
      expect(service.hayActividadActiva([], [])).toBe(false);
    });
  });

  it('configVigente elige la configuración del periodo más reciente del tipo', () => {
    const configs = [
      { tipo: 'PRACTICAS', periodoAcademico: '2025-2' },
      { tipo: 'PRACTICAS', periodoAcademico: '2026-2' },
      { tipo: 'VINCULACION', periodoAcademico: '2026-1' }
    ];
    expect(service.configVigente(configs, 'PRACTICAS')?.periodoAcademico).toBe('2026-2');
    expect(service.configVigente(configs, 'VINCULACION')?.periodoAcademico).toBe('2026-1');
    expect(service.configVigente([], 'PRACTICAS')).toBeUndefined();
  });

  it('soloTutores filtra por el código de rol TUTOR', () => {
    const usuarios = [
      { email: 't@x.ec', roles: [{ codigo: 'TUTOR' }] },
      { email: 'a@x.ec', roles: [{ codigo: 'ADMIN' }] },
      { email: 's@x.ec' }
    ] as Usuario[];
    expect(service.soloTutores(usuarios).map(u => u.email)).toEqual(['t@x.ec']);
  });

  it('soloTutores respeta el área real y excluye cuentas inactivas', () => {
    const usuarios = [
      { email: 'p@x.ec', activo: true, tutorTipo: 'PRACTICAS', roles: [{ codigo: 'TUTOR' }] },
      { email: 'v@x.ec', activo: true, tutorTipo: 'VINCULACION', roles: [{ codigo: 'TUTOR' }] },
      { email: 'a@x.ec', activo: true, tutorTipo: 'AMBOS', roles: [{ codigo: 'TUTOR' }] },
      { email: 'i@x.ec', activo: false, tutorTipo: 'PRACTICAS', roles: [{ codigo: 'TUTOR' }] }
    ] as Usuario[];
    expect(service.soloTutores(usuarios, 'PRACTICAS').map(u => u.email)).toEqual(['p@x.ec', 'a@x.ec']);
  });

  // Fase 9: fuentes por rol — el backend restringe los listados, asi que
  // estudiante y tutor deben consultar /me en lugar del listado completo
  describe('loaders segun rol', () => {
    let httpMock: HttpTestingController;

    beforeEach(() => {
      httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => httpMock.verify());

    it('estudiantesSegunRol(ESTUDIANTE) consulta /me y envuelve en lista', () => {
      let resultado: unknown;
      service.estudiantesSegunRol('ESTUDIANTE').subscribe(r => (resultado = r));
      httpMock.expectOne('/api/estudiantes/me').flush({ id: 7, matricula: 'EST-1' });
      expect(resultado).toEqual([{ id: 7, matricula: 'EST-1' }]);
    });

    it('estudiantesSegunRol(ESTUDIANTE) sin expediente devuelve lista vacía', () => {
      let resultado: unknown;
      service.estudiantesSegunRol('ESTUDIANTE').subscribe(r => (resultado = r));
      httpMock.expectOne('/api/estudiantes/me').flush(null, { status: 404, statusText: 'Not Found' });
      expect(resultado).toEqual([]);
    });

    it('estudiantesSegunRol(COORDINADOR) no consulta el listado: usa autocompletado asíncrono', () => {
      let resultado: unknown;
      service.estudiantesSegunRol('COORDINADOR').subscribe(r => (resultado = r));
      httpMock.expectNone('/api/estudiantes');
      expect(resultado).toEqual([]);
    });

    it('usuariosSegunRol usa /me para TUTOR, tutores para COORDINADOR y listado completo para ADMIN', () => {
      service.usuariosSegunRol('TUTOR').subscribe();
      httpMock.expectOne('/api/usuarios/me').flush({ id: 1 });

      service.usuariosSegunRol('COORDINADOR').subscribe();
      httpMock.expectOne('/api/usuarios/tutores').flush([]);

      service.usuariosSegunRol('ADMIN').subscribe();
      httpMock.expectOne('/api/usuarios').flush([]);
    });

    it('practicasSegunRol usa rutas personales para estudiante y tutor', () => {
      service.practicasSegunRol('ESTUDIANTE').subscribe();
      httpMock.expectOne('/api/practicas/me').flush([]);

      service.practicasSegunRol('TUTOR').subscribe();
      httpMock.expectOne('/api/practicas/tutor/me').flush([]);

      service.practicasSegunRol('COORDINADOR').subscribe();
      httpMock.expectOne('/api/practicas').flush([]);
    });

    it('vinculacionesSegunRol usa rutas personales para estudiante y tutor', () => {
      service.vinculacionesSegunRol('ESTUDIANTE').subscribe();
      httpMock.expectOne('/api/vinculacion/me').flush([]);

      service.vinculacionesSegunRol('TUTOR').subscribe();
      httpMock.expectOne('/api/vinculacion/tutor/me').flush([]);

      service.vinculacionesSegunRol('ADMIN').subscribe();
      httpMock.expectOne('/api/vinculacion').flush([]);
    });

    it('postulaciones y seguimiento personal no usan ids enviados por el cliente', () => {
      service.postulacionesPracticasSegunRol('ESTUDIANTE').subscribe();
      httpMock.expectOne('/api/postulaciones/me').flush([]);

      service.postulacionesVinculacionSegunRol('ESTUDIANTE').subscribe();
      httpMock.expectOne('/api/vinculacion/postulaciones/me').flush([]);

      service.bitacorasSegunRol('ESTUDIANTE').subscribe();
      httpMock.expectOne('/api/bitacoras/me').flush([]);

      service.asistenciasSegunRol('ESTUDIANTE').subscribe();
      httpMock.expectOne('/api/asistencias/me').flush([]);
    });
  });
});
