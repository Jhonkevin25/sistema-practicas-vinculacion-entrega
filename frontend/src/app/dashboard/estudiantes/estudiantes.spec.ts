import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../core/services/auth.service';
import { CarreraService } from '../../core/services/carrera.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { DocumentoService } from '../../core/services/documento.service';
import { Estudiante, EstudianteService } from '../../core/services/estudiante.service';
import { ExpedienteService } from '../../core/services/expediente.service';
import { NotaAcademicaService } from '../../core/services/nota-academica.service';
import { ToastService } from '../../core/services/toast.service';
import { UsuarioService } from '../../core/services/usuario.service';
import { EstudiantesComponent } from './estudiantes';

const estudiante: Estudiante = {
  id: 8,
  usuario: {
    id: 20,
    nombre: 'Ana',
    apellido: 'Torres',
    email: 'ana@est.unibe.edu.ec'
  },
  matricula: 'MVP-001',
  carrera: 'Derecho',
  semestre: 4,
  periodoAcademico: '2026-2'
};

describe('gestión de estudiantes por rol', () => {
  afterEach(() => TestBed.resetTestingModule());

  function preparar(rol: 'ADMIN' | 'COORDINADOR') {
    const getCandidatosEstudiante = vi.fn(() => of([estudiante.usuario]));
    const auth = {
      getRole: vi.fn(() => rol),
      currentUser: signal({
        token: 'token-prueba',
        nombre: 'Usuario',
        apellido: 'Prueba',
        email: 'usuario@unibe.edu.ec',
        rol
      }),
      carrerasAsignadas: signal(['Derecho'])
    };

    TestBed.configureTestingModule({
      imports: [EstudiantesComponent],
      providers: [
        {
          provide: EstudianteService,
          useValue: {
            getPaginado: vi.fn(() => of({
              contenido: [estudiante],
              pagina: 0,
              tamano: 20,
              totalElementos: 1,
              totalPaginas: 1,
              ultima: true
            })),
            create: vi.fn(),
            update: vi.fn(),
            delete: vi.fn()
          }
        },
        { provide: UsuarioService, useValue: { getCandidatosEstudiante } },
        { provide: AuthService, useValue: auth },
        { provide: ToastService, useValue: { success: vi.fn(), warning: vi.fn(), error: vi.fn() } },
        { provide: ConfirmService, useValue: { confirm: vi.fn() } },
        { provide: NotaAcademicaService, useValue: { getAll: vi.fn(() => of([])) } },
        { provide: DocumentoService, useValue: { getRequeridos: vi.fn(() => of([])) } },
        { provide: CarreraService, useValue: { getAll: vi.fn(() => of([])) } },
        { provide: ExpedienteService, useValue: { periodoActual: vi.fn(() => '2026-2') } }
      ]
    });

    const fixture = TestBed.createComponent(EstudiantesComponent);
    fixture.detectChanges();
    return { fixture, getCandidatosEstudiante };
  }

  it('ADMIN carga candidatos y ve alta y baja de estudiantes', () => {
    const { fixture, getCandidatosEstudiante } = preparar('ADMIN');
    const host = fixture.nativeElement as HTMLElement;

    expect(getCandidatosEstudiante).toHaveBeenCalledOnce();
    expect(host.textContent).toContain('Nuevo Estudiante');
    expect(host.querySelector('[aria-label^="Eliminar estudiante"]')).not.toBeNull();
  });

  it('COORDINADOR no consulta candidatos ni ve controles de alta o baja', () => {
    const { fixture, getCandidatosEstudiante } = preparar('COORDINADOR');
    const host = fixture.nativeElement as HTMLElement;

    expect(getCandidatosEstudiante).not.toHaveBeenCalled();
    expect(host.textContent).not.toContain('Nuevo Estudiante');
    expect(host.querySelector('[aria-label^="Eliminar estudiante"]')).toBeNull();
  });

  it('COORDINADOR edita solo semestre y periodo', () => {
    const { fixture } = preparar('COORDINADOR');
    fixture.componentInstance.openEditModal(estudiante);
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;

    expect(host.querySelector('[name="usuarioId"]')).toBeNull();
    expect(host.querySelector('[name="matricula"]')).toBeNull();
    expect(host.querySelector('[name="carrera"]')).toBeNull();
    expect(host.querySelector('[name="semestre"]')).not.toBeNull();
    expect(host.querySelector('[name="periodoAcademico"]')).not.toBeNull();
    expect(host.textContent).toContain('Cuenta vinculada');
  });
});
