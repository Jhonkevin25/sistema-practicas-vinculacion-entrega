import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { Observable, shareReplay } from 'rxjs';
import { Estudiante } from './estudiante.service';
import { Empresa } from './empresa.service';
import { Usuario } from './usuario.service';
import { VacantePractica } from './vacante.service';
import { Paginado, paramsPaginado } from './paginacion';
import { SKIP_ERROR_TOAST } from '../interceptors/http-context-tokens';

export interface Practica {
  id?: number;
  estudiante: Estudiante;
  empresa: Empresa;
  vacante?: VacantePractica;
  tipoAsignacion?: 'LEGACY' | 'MERITOCRACIA' | 'DIRECTA' | 'CONTINUIDAD';
  practicaOrigenId?: number | null;
  asignacionSnapshot?: Record<string, unknown> | null;
  tutor?: Usuario;
  encargado?: Usuario;
  estado?: string;
  horasRequeridas: number;
  horasCompletadas?: number;
  fechaInicio?: string;
  fechaFin?: string;
  periodoAcademico?: string;
  motivoFinalizacion?: string;
  finalizadoEn?: string;
  cerradoEn?: string;
  cupoLiberado?: boolean;
}

export interface SeguimientoPractica {
  practicaId: number;
  estudianteId: number;
  estudiante: string;
  matricula?: string;
  carrera?: string;
  empresa?: string;
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
export class PracticaService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/practicas';


  private cacheAll$: Observable<Practica[]> | null = null;
  private cachePaginado$: Observable<Paginado<Practica>> | null = null;
  private lastParamsKey = '';

  getAll(forceRefresh = false): Observable<Practica[]> {
    if (forceRefresh || !this.cacheAll$) {
      this.cacheAll$ = this.http.get<Practica[]>(this.apiUrl).pipe(shareReplay(1));
    }
    return this.cacheAll$;
  }

  // Fase 43: listado paginado en BD
  getPaginado(pagina: number, tamano: number,
              filtros: { texto?: string; estado?: string; periodoAcademico?: string } = {},
              sortBy?: string, sortDir?: string): Observable<Paginado<Practica>> {
    const key = JSON.stringify({ pagina, tamano, filtros, sortBy, sortDir });
    if (this.cachePaginado$ && this.lastParamsKey === key) {
      return this.cachePaginado$;
    }
    this.lastParamsKey = key;
    this.cachePaginado$ = this.http.get<Paginado<Practica>>(`${this.apiUrl}/paginado`, {
      params: paramsPaginado(pagina, tamano, filtros, sortBy, sortDir)
    }).pipe(shareReplay(1));
    return this.cachePaginado$;
  }

  private cacheMine$: Observable<Practica[]> | null = null;
  private cacheTutorMine$: Observable<Practica[]> | null = null;

  getMine(): Observable<Practica[]> {
    if (!this.cacheMine$) {
      this.cacheMine$ = this.http.get<Practica[]>(`${this.apiUrl}/me`).pipe(shareReplay(1));
    }
    return this.cacheMine$;
  }

  getTutorMine(): Observable<Practica[]> {
    if (!this.cacheTutorMine$) {
      this.cacheTutorMine$ = this.http.get<Practica[]>(`${this.apiUrl}/tutor/me`).pipe(shareReplay(1));
    }
    return this.cacheTutorMine$;
  }

  getById(id: number): Observable<Practica> {
    return this.http.get<Practica>(`${this.apiUrl}/${id}`);
  }

  getByEstudianteId(estudianteId: number): Observable<Practica[]> {
    return this.http.get<Practica[]>(`${this.apiUrl}/estudiante/${estudianteId}`);
  }

  getByTutorId(tutorId: number): Observable<Practica[]> {
    return this.http.get<Practica[]>(`${this.apiUrl}/tutor/${tutorId}`);
  }

  getByEncargadoId(encargadoId: number): Observable<Practica[]> {
    return this.http.get<Practica[]>(`${this.apiUrl}/encargado/${encargadoId}`);
  }

  private cacheSeguimientoPaginado$: Observable<Paginado<SeguimientoPractica>> | null = null;
  private lastSeguimientoParamsKey = '';

  // silencioso=true: uso en polling de fondo (dashboard/seguimiento), evita
  // el toast global de error del interceptor ante un fallo de red puntual.
  getSeguimiento(silencioso = false): Observable<SeguimientoPractica[]> {
    return this.http.get<SeguimientoPractica[]>(`${this.apiUrl}/seguimiento`, silencioso
      ? { context: new HttpContext().set(SKIP_ERROR_TOAST, true) }
      : {});
  }

  // Fase 43: Seguimiento paginado en BD
  getSeguimientoPaginado(pagina: number, tamano: number,
                         filtros: { texto?: string; estado?: string; periodoAcademico?: string;
                                    carrera?: string; tutorId?: string | number } = {},
                         sortBy?: string, sortDir?: string): Observable<Paginado<SeguimientoPractica>> {
    const key = JSON.stringify({ pagina, tamano, filtros, sortBy, sortDir });
    if (this.cacheSeguimientoPaginado$ && this.lastSeguimientoParamsKey === key) {
      return this.cacheSeguimientoPaginado$;
    }
    this.lastSeguimientoParamsKey = key;
    this.cacheSeguimientoPaginado$ = this.http.get<Paginado<SeguimientoPractica>>(`${this.apiUrl}/seguimiento/paginado`, {
      params: paramsPaginado(pagina, tamano, filtros, sortBy, sortDir)
    }).pipe(shareReplay(1));
    return this.cacheSeguimientoPaginado$;
  }

  // Publico: otros servicios relacionados (bitacoras, asistencias, notas de
  // coordinacion) invalidan este cache cuando modifican datos que afectan
  // horas/avance/estado de la practica, para que loadData() no reponga un
  // shareReplay(1) obsoleto.
  clearCache(): void {
    this.cacheAll$ = null;
    this.cachePaginado$ = null;
    this.cacheSeguimientoPaginado$ = null;
    this.cacheMine$ = null;
    this.cacheTutorMine$ = null;
  }

  create(practica: Practica): Observable<Practica> {
    this.clearCache();
    return this.http.post<Practica>(this.apiUrl, practica);
  }

  update(id: number, practica: Practica): Observable<Practica> {
    this.clearCache();
    return this.http.put<Practica>(`${this.apiUrl}/${id}`, practica);
  }

  cerrar(id: number): Observable<Practica> {
    this.clearCache();
    return this.http.post<Practica>(`${this.apiUrl}/${id}/cerrar`, {});
  }

  // Fase 47: liberación única y justificada del cupo de empresa de un retiro
  liberarCupo(id: number, motivo: string): Observable<Practica> {
    this.clearCache();
    return this.http.post<Practica>(`${this.apiUrl}/${id}/liberar-cupo`, { motivo });
  }

  getActa(id: number): Observable<ActaExpediente> {
    return this.http.get<ActaExpediente>(`${this.apiUrl}/${id}/acta`);
  }

  delete(id: number): Observable<void> {
    this.clearCache();
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
