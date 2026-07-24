import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { EstudianteService, Estudiante } from './estudiante.service';
import { UsuarioService, Usuario } from './usuario.service';
import { PracticaService, Practica } from './practica.service';
import { VinculacionService, Vinculacion, PostulacionVinculacion } from './vinculacion.service';
import { PostulacionService, PostulacionMeritocratica } from './postulacion.service';
import { BitacoraService, Bitacora } from './bitacora.service';
import { AsistenciaService, Asistencia } from './asistencia.service';
import { Convenio } from './convenio.service';
import { PeriodoAcademicoService } from './periodo-academico.service';

export type EtapaAcademica = 'Vinculación' | 'Práctica I' | 'Práctica II' | 'COMPLETADO';

// Logica academica compartida por practicas, vinculacion y overview
// (Fase 8: antes duplicada en cada componente)
@Injectable({ providedIn: 'root' })
export class ExpedienteService {
  private readonly estudianteService = inject(EstudianteService);
  private readonly usuarioService = inject(UsuarioService);
  private readonly practicaService = inject(PracticaService);
  private readonly vinculacionService = inject(VinculacionService);
  private readonly postulacionService = inject(PostulacionService);
  private readonly bitacoraService = inject(BitacoraService);
  private readonly asistenciaService = inject(AsistenciaService);
  private readonly periodoAcademicoService = inject(PeriodoAcademicoService);

  // Periodo academico actual, ej. 2026-2
  periodoActual(): string {
    const institucional = this.periodoAcademicoService.activo();
    if (institucional) return institucional.codigo;
    const now = new Date();
    return `${now.getFullYear()}-${now.getMonth() < 6 ? 1 : 2}`;
  }

  // Fuentes segun rol: el backend restringe /api/estudiantes y /api/usuarios,
  // asi que estudiante y tutor solo consultan su propio registro via /me
  estudiantesSegunRol(rol: string): Observable<Estudiante[]> {
    if (rol === 'ESTUDIANTE') {
      return this.estudianteService.getMe().pipe(map(e => [e]), catchError(() => of([] as Estudiante[])));
    }
    // Para ADMIN y COORDINADOR retornamos vacío porque usan el autocompletado asíncrono
    return of([] as Estudiante[]);
  }

  usuariosSegunRol(rol: string): Observable<Usuario[]> {
    if (rol === 'ADMIN') return this.usuarioService.getAll();
    if (rol === 'COORDINADOR') return this.usuarioService.getTutores();
    return this.usuarioService.getMe().pipe(map(u => [u]), catchError(() => of([] as Usuario[])));
  }

  practicasSegunRol(rol: string): Observable<Practica[]> {
    if (rol === 'ESTUDIANTE') return this.practicaService.getMine();
    if (rol === 'TUTOR') return this.practicaService.getTutorMine();
    return this.practicaService.getAll();
  }

  vinculacionesSegunRol(rol: string): Observable<Vinculacion[]> {
    if (rol === 'ESTUDIANTE') return this.vinculacionService.getMine();
    if (rol === 'TUTOR') return this.vinculacionService.getTutorMine();
    return this.vinculacionService.getAll();
  }

  postulacionesPracticasSegunRol(rol: string): Observable<PostulacionMeritocratica[]> {
    if (rol === 'ESTUDIANTE') return this.postulacionService.getMine();
    if (rol === 'TUTOR') return of([] as PostulacionMeritocratica[]);
    return this.postulacionService.getAll();
  }

  postulacionesVinculacionSegunRol(rol: string): Observable<PostulacionVinculacion[]> {
    if (rol === 'ESTUDIANTE') return this.vinculacionService.getMisPostulaciones();
    if (rol === 'TUTOR') return of([] as PostulacionVinculacion[]);
    return this.vinculacionService.getPostulaciones();
  }

  bitacorasSegunRol(rol: string): Observable<Bitacora[]> {
    if (rol === 'ESTUDIANTE') return this.bitacoraService.getMine();
    if (rol === 'TUTOR') return this.bitacoraService.getAll(); // El backend ya filtra por el tutor
    return of([] as Bitacora[]); // ADMIN y COORDINADOR usan lazy loading
  }

  asistenciasSegunRol(rol: string): Observable<Asistencia[]> {
    if (rol === 'ESTUDIANTE') return this.asistenciaService.getMine();
    if (rol === 'TUTOR') return this.asistenciaService.getAll(); // El backend ya filtra por el tutor
    return of([] as Asistencia[]); // ADMIN y COORDINADOR usan lazy loading
  }

  // Solo usuarios con rol TUTOR (dropdowns de asignacion)
  soloTutores(usuarios: Usuario[], proceso?: 'PRACTICAS' | 'VINCULACION'): Usuario[] {
    return usuarios.filter(u => {
      const esTutorActivo = u.activo !== false
        && (u.roles || []).some((r: any) => r.codigo === 'TUTOR');
      if (!esTutorActivo || !proceso) return esTutorActivo;
      const tipo = String(u.tutorTipo || '').trim().toUpperCase();
      return tipo === 'AMBOS' || tipo === proceso;
    });
  }

  // Etapa academica REAL derivada del expediente:
  // Vinculación (160h) -> Práctica I (120h) -> Práctica II (120h)
  etapaDe(practicas: Practica[], vinculaciones: Vinculacion[]): EtapaAcademica {
    const vinculacionCompletada = vinculaciones.some(v => v.estado === 'completado');
    if (!vinculacionCompletada) return 'Vinculación';
    const practicasCompletadas = practicas.filter(p => p.estado === 'completado').length;
    if (practicasCompletadas === 0) return 'Práctica I';
    if (practicasCompletadas === 1) return 'Práctica II';
    return 'COMPLETADO';
  }

  // Regla de exclusividad: hay un proceso activo (pendiente o en curso)
  hayActividadActiva(practicas: Practica[], vinculaciones: Vinculacion[]): boolean {
    return practicas.some(p => p.estado === 'en_curso' || p.estado === 'pendiente')
      || vinculaciones.some(v => v.estado === 'en_curso' || v.estado === 'pendiente');
  }

  // Configuracion de convocatoria vigente: la del periodo mas reciente del tipo
  configVigente<T extends { tipo?: string; periodoAcademico?: string }>(
    configuraciones: T[], tipo: 'PRACTICAS' | 'VINCULACION'): T | undefined {
    return configuraciones
      .filter(c => c.tipo === tipo)
      .sort((a, b) => String(b.periodoAcademico).localeCompare(String(a.periodoAcademico)))[0];
  }

  // Comparacion de carreras insensible a mayusculas y tildes:
  // "Ingenieria en Software" e "Ingeniería en Software" son la misma carrera
  mismaCarrera(a?: string | null, b?: string | null): boolean {
    if (!a || !b) return false;
    const norm = (s: string) => s.trim().toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
    return norm(a) === norm(b);
  }

  // Espejo de ConvenioVigenteComponent (backend): motivo por el que la entidad
  // no puede recibir estudiantes de esa carrera, o null si un convenio vigente
  // la cubre. Compartido por practicas (EMPRESA) y vinculacion (FUNDACION).
  motivoConvenioInvalido(convenios: Convenio[], entidad: 'EMPRESA' | 'FUNDACION',
                         entidadId: number | null | undefined, carrera?: string): string | null {
    if (entidadId == null) return 'Entidad sin identificar';
    const deEntidad = convenios.filter(c =>
      (entidad === 'EMPRESA' ? c.empresa?.id : c.fundacion?.id) === entidadId);
    if (deEntidad.length === 0) return 'Sin convenio';
    const hoy = new Date().toISOString().split('T')[0];
    const vigentes = deEntidad.filter(c =>
      c.estado === 'VIGENTE' && c.fechaInicio <= hoy && c.fechaFin >= hoy);
    if (vigentes.length === 0) return 'Convenio no vigente';
    const cubre = vigentes.some(c => !c.carreras?.length
      || (!!carrera && c.carreras.some(cubierta => this.mismaCarrera(cubierta, carrera))));
    return cubre ? null : 'Convenio no cubre la carrera';
  }
}
