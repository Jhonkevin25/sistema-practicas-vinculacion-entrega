import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { Observable, shareReplay } from 'rxjs';
import { Estudiante } from './estudiante.service';
import { Fundacion } from './fundacion.service';
import { Proyecto } from './proyecto.service';
import { Usuario } from './usuario.service';
import { Paginado, paramsPaginado } from './paginacion';
import { SKIP_ERROR_TOAST } from '../interceptors/http-context-tokens';

export interface Vinculacion {
  id?: number;
  estudiante: Estudiante;
  fundacion: Fundacion;
  proyecto: Proyecto;
  tutor?: Usuario;
  encargado?: Usuario;
  estado?: string;
  horasRequeridas?: number;
  horasCompletadas?: number;
  fechaInicio?: string;
  fechaFin?: string;
  periodoAcademico?: string;
  motivoFinalizacion?: string;
  finalizadoEn?: string;
  // Fase 42: liberación única del cupo de una vinculación retirada
  cupoLiberado?: boolean;
  motivoLiberacionCupo?: string;
}

export interface PostulacionVinculacion {
  id?: number;
  estudiante: Estudiante;
  proyecto: Proyecto;
  estado?: string;
  periodoAcademico?: string;
  vinculacion?: Vinculacion;
  observacion?: string;
  aprobadoPor?: Usuario;
  aprobadoEn?: string;
  createdAt?: string;
}

export interface SeguimientoVinculacion {
  vinculacionId: number;
  estudianteId: number;
  estudiante: string;
  matricula?: string;
  carrera?: string;
  proyecto?: string;
  tutor?: string;
  periodoAcademico?: string;
  estado?: string;
  motivoFinalizacion?: string;
  finalizadoEn?: string;
  horasRequeridas?: number;
  horasCompletadas?: number;
  porcentajeAvance: number;
  bitacorasPendientes: number;
  bitacorasRechazadas: number;
  bitacorasCorreccion: number;
  asistenciasRegistradas: number;
  diasSinActividad?: number | null;
  parcialesCerrados: number;
  encuestasCompletadas: number;
  notasTutorPendientes: number;
  notasCoordPendientes: number;
  encuestasPendientes: number;
  listoParaCierre: boolean;
  riesgo: boolean;
  alertas: string[];
}

export type ActaExpediente = Record<string, unknown>;

@Injectable({
  providedIn: 'root'
})
export class VinculacionService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/vinculacion';

  private cacheAll$: Observable<Vinculacion[]> | null = null;
  private cachePaginado$: Observable<Paginado<Vinculacion>> | null = null;
  private lastParamsKey = '';

  private cacheSeguimiento$: Observable<SeguimientoVinculacion[]> | null = null;
  private cacheSeguimientoPaginado$: Observable<Paginado<SeguimientoVinculacion>> | null = null;
  private lastSeguimientoParamsKey = '';

  private cachePostulaciones$: Observable<PostulacionVinculacion[]> | null = null;

  private cacheMine$: Observable<Vinculacion[]> | null = null;
  private cacheTutorMine$: Observable<Vinculacion[]> | null = null;
  private cacheMisPostulaciones$: Observable<PostulacionVinculacion[]> | null = null;

  getAll(forceRefresh = false): Observable<Vinculacion[]> {
    if (forceRefresh || !this.cacheAll$) {
      this.cacheAll$ = this.http.get<Vinculacion[]>(this.apiUrl).pipe(shareReplay(1));
    }
    return this.cacheAll$;
  }

  // Fase 43: listado paginado en BD
  getPaginado(pagina: number, tamano: number,
              filtros: { texto?: string; estado?: string; periodoAcademico?: string } = {},
              sortBy?: string, sortDir?: string): Observable<Paginado<Vinculacion>> {
    const key = JSON.stringify({ pagina, tamano, filtros, sortBy, sortDir });
    if (this.cachePaginado$ && this.lastParamsKey === key) {
      return this.cachePaginado$;
    }
    this.lastParamsKey = key;
    this.cachePaginado$ = this.http.get<Paginado<Vinculacion>>(`${this.apiUrl}/paginado`, {
      params: paramsPaginado(pagina, tamano, filtros, sortBy, sortDir)
    }).pipe(shareReplay(1));
    return this.cachePaginado$;
  }

  getMine(): Observable<Vinculacion[]> {
    if (!this.cacheMine$) {
      this.cacheMine$ = this.http.get<Vinculacion[]>(`${this.apiUrl}/me`).pipe(shareReplay(1));
    }
    return this.cacheMine$;
  }

  getTutorMine(): Observable<Vinculacion[]> {
    if (!this.cacheTutorMine$) {
      this.cacheTutorMine$ = this.http.get<Vinculacion[]>(`${this.apiUrl}/tutor/me`).pipe(shareReplay(1));
    }
    return this.cacheTutorMine$;
  }

  getById(id: number): Observable<Vinculacion> {
    return this.http.get<Vinculacion>(`${this.apiUrl}/${id}`);
  }

  getByEstudianteId(estudianteId: number): Observable<Vinculacion[]> {
    return this.http.get<Vinculacion[]>(`${this.apiUrl}/estudiante/${estudianteId}`);
  }

  getByTutorId(tutorId: number): Observable<Vinculacion[]> {
    return this.http.get<Vinculacion[]>(`${this.apiUrl}/tutor/${tutorId}`);
  }

  getByEncargadoId(encargadoId: number): Observable<Vinculacion[]> {
    return this.http.get<Vinculacion[]>(`${this.apiUrl}/encargado/${encargadoId}`);
  }

  // silencioso=true: uso en polling de fondo (dashboard/seguimiento), evita
  // el toast global de error del interceptor ante un fallo de red puntual.
  getSeguimiento(silencioso = false): Observable<SeguimientoVinculacion[]> {
    return this.http.get<SeguimientoVinculacion[]>(`${this.apiUrl}/seguimiento`, silencioso
      ? { context: new HttpContext().set(SKIP_ERROR_TOAST, true) }
      : {});
  }

  // Fase 43: Seguimiento paginado en BD
  getSeguimientoPaginado(pagina: number, tamano: number,
                         filtros: { texto?: string; estado?: string; periodoAcademico?: string;
                                    carrera?: string; tutorId?: string | number } = {},
                         sortBy?: string, sortDir?: string): Observable<Paginado<SeguimientoVinculacion>> {
    const key = JSON.stringify({ pagina, tamano, filtros, sortBy, sortDir });
    if (this.cacheSeguimientoPaginado$ && this.lastSeguimientoParamsKey === key) {
      return this.cacheSeguimientoPaginado$;
    }
    this.lastSeguimientoParamsKey = key;
    this.cacheSeguimientoPaginado$ = this.http.get<Paginado<SeguimientoVinculacion>>(`${this.apiUrl}/seguimiento/paginado`, {
      params: paramsPaginado(pagina, tamano, filtros, sortBy, sortDir)
    }).pipe(shareReplay(1));
    return this.cacheSeguimientoPaginado$;
  }

  getPostulaciones(): Observable<PostulacionVinculacion[]> {
    if (!this.cachePostulaciones$) {
      this.cachePostulaciones$ = this.http.get<PostulacionVinculacion[]>(`${this.apiUrl}/postulaciones`).pipe(shareReplay(1));
    }
    return this.cachePostulaciones$;
  }

  getMisPostulaciones(): Observable<PostulacionVinculacion[]> {
    if (!this.cacheMisPostulaciones$) {
      this.cacheMisPostulaciones$ = this.http.get<PostulacionVinculacion[]>(`${this.apiUrl}/postulaciones/me`).pipe(shareReplay(1));
    }
    return this.cacheMisPostulaciones$;
  }

  // Publico: otros servicios relacionados (bitacoras, asistencias, notas de
  // coordinacion) invalidan este cache cuando modifican datos que afectan
  // horas/avance/estado de la vinculacion, para que loadData() no reponga un
  // shareReplay(1) obsoleto.
  clearCache(): void {
    this.cacheAll$ = null;
    this.cachePaginado$ = null;
    this.cacheSeguimientoPaginado$ = null;
    this.cacheMine$ = null;
    this.cacheTutorMine$ = null;
    this.cachePostulaciones$ = null;
    this.cacheMisPostulaciones$ = null;
  }

  postularProyecto(proyectoId: number): Observable<PostulacionVinculacion> {
    this.clearCache();
    return this.http.post<PostulacionVinculacion>(`${this.apiUrl}/postulaciones`, {
      proyecto: { id: proyectoId }
    });
  }

  aprobarPostulacion(id: number, vinculacion: Partial<Vinculacion>): Observable<PostulacionVinculacion> {
    this.clearCache();
    return this.http.post<PostulacionVinculacion>(`${this.apiUrl}/postulaciones/${id}/aprobar`, vinculacion);
  }

  rechazarPostulacion(id: number, observacion: string): Observable<PostulacionVinculacion> {
    this.clearCache();
    return this.http.post<PostulacionVinculacion>(`${this.apiUrl}/postulaciones/${id}/rechazar`, { observacion });
  }

  create(vinculacion: Vinculacion): Observable<Vinculacion> {
    this.clearCache();
    return this.http.post<Vinculacion>(this.apiUrl, vinculacion);
  }

  update(id: number, vinculacion: Vinculacion): Observable<Vinculacion> {
    this.clearCache();
    return this.http.put<Vinculacion>(`${this.apiUrl}/${id}`, vinculacion);
  }

  cerrar(id: number): Observable<Vinculacion> {
    this.clearCache();
    return this.http.post<Vinculacion>(`${this.apiUrl}/${id}/cerrar`, {});
  }

  // Fase 42: libera (una sola vez, con justificación) el cupo de una
  // vinculación retirada para permitir un reemplazo en el mismo periodo
  liberarCupo(id: number, motivo: string): Observable<Vinculacion> {
    this.clearCache();
    return this.http.post<Vinculacion>(`${this.apiUrl}/${id}/liberar-cupo`, { motivo });
  }

  getActa(id: number): Observable<ActaExpediente> {
    return this.http.get<ActaExpediente>(`${this.apiUrl}/${id}/acta`);
  }

  delete(id: number): Observable<void> {
    this.clearCache();
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
