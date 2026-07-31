import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';
import { Paginado, paramsPaginado } from './paginacion';

export type TipoReporte = 'ASIGNACIONES' | 'CUPOS' | 'RIESGOS' | 'CIERRES';
export type ProcesoReporte = 'TODOS' | 'PRACTICAS' | 'VINCULACION';

export interface FiltrosReporte {
  periodo?: string;
  carrera?: string;
  proceso?: ProcesoReporte;
  entidad?: string;
}

export interface AsignacionReporte {
  proceso: string;
  expedienteId: number;
  estudianteId: number;
  estudiante: string;
  matricula: string;
  carrera: string;
  periodo: string;
  entidad: string;
  estado: string;
  fechaInicio: string | null;
  fechaFin: string | null;
  horasRequeridas: number;
  horasCompletadas: number;
}

export interface CupoReporte {
  proceso: string;
  entidad: string;
  carrera: string;
  periodo: string;
  cuposTotales: number;
  cuposUsados: number;
  cuposDisponibles: number;
  ocupacionPorcentaje: number;
}

export interface RiesgoReporte {
  proceso: string;
  expedienteId: number;
  estudianteId: number;
  estudiante: string;
  carrera: string;
  periodo: string;
  entidad: string;
  estado: string;
  diasSinActividad: number | null;
  horasCompletadas: number;
  horasRequeridas: number;
  avancePorcentaje: number;
  bitacorasObservadas: number;
  nivel: 'ALTO' | 'MEDIO';
  motivos: string[];
}

export interface ConteosOverview {
  estudiantes: number;
  empresas: number;
  practicas: number;
  vinculaciones: number;
}

export interface DistribucionEstadoReporte {
  estado: string;
  cantidad: number;
}

export interface CupoEntidadReporte {
  entidad: string;
  disponibles: number;
}

export interface CierreReporte {
  proceso: string;
  expedienteId: number;
  estudianteId: number;
  estudiante: string;
  carrera: string;
  periodo: string;
  entidad: string;
  estado: string;
  horasRequeridas: number;
  horasCompletadas: number;
  cumplimientoPorcentaje: number;
  cerradoEn: string | null;
  cerrado: boolean;
  cumpleHoras: boolean;
}

@Injectable({ providedIn: 'root' })
export class ReporteService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/reportes';

  // Cache con vencimiento corto (no invalidacion manual): estas 3 respuestas
  // dependen de escrituras dispersas en todo el sistema (bitacoras,
  // asistencias, evaluaciones, empresas...), no hay un solo punto de
  // "clearCache()" razonable como en Practica/Vinculacion/Empresa. Un TTL
  // corto evita releer todo al ir y volver de Inicio sin arriesgar datos
  // desactualizados por mucho tiempo.
  private readonly OVERVIEW_TTL_MS = 60_000;
  private cacheConteosOverview$: Observable<ConteosOverview> | null = null;
  private cacheConteosOverviewAt = 0;
  private cacheDistribucionEstados$: Observable<DistribucionEstadoReporte[]> | null = null;
  private cacheDistribucionEstadosAt = 0;
  private cacheCuposTopEntidades$: Observable<CupoEntidadReporte[]> | null = null;
  private cacheCuposTopEntidadesAt = 0;

  private vigente(desde: number): boolean {
    return Date.now() - desde < this.OVERVIEW_TTL_MS;
  }

  asignaciones(filtros: FiltrosReporte): Observable<AsignacionReporte[]> {
    return this.http.get<AsignacionReporte[]>(`${this.apiUrl}/asignaciones`, { params: this.params(filtros) });
  }

  asignacionesPaginadas(pagina: number, tamano: number, filtros: FiltrosReporte): Observable<Paginado<AsignacionReporte>> {
    let httpParams = this.params(filtros);
    httpParams = httpParams.set('pagina', pagina.toString()).set('tamano', tamano.toString());
    return this.http.get<Paginado<AsignacionReporte>>(`${this.apiUrl}/asignaciones/paginado`, { params: httpParams });
  }

  cupos(filtros: FiltrosReporte): Observable<CupoReporte[]> {
    return this.http.get<CupoReporte[]>(`${this.apiUrl}/cupos`, { params: this.params(filtros) });
  }

  cuposPaginados(pagina: number, tamano: number, filtros: FiltrosReporte): Observable<Paginado<CupoReporte>> {
    let httpParams = this.params(filtros);
    httpParams = httpParams.set('pagina', pagina.toString()).set('tamano', tamano.toString());
    return this.http.get<Paginado<CupoReporte>>(`${this.apiUrl}/cupos/paginado`, { params: httpParams });
  }

  riesgos(filtros: FiltrosReporte): Observable<RiesgoReporte[]> {
    return this.http.get<RiesgoReporte[]>(`${this.apiUrl}/riesgos`, { params: this.params(filtros) });
  }

  riesgosPaginados(pagina: number, tamano: number, filtros: FiltrosReporte): Observable<Paginado<RiesgoReporte>> {
    let httpParams = this.params(filtros);
    httpParams = httpParams.set('pagina', pagina.toString()).set('tamano', tamano.toString());
    return this.http.get<Paginado<RiesgoReporte>>(`${this.apiUrl}/riesgos/paginado`, { params: httpParams });
  }

  cierres(filtros: FiltrosReporte): Observable<CierreReporte[]> {
    return this.http.get<CierreReporte[]>(`${this.apiUrl}/cierres`, { params: this.params(filtros) });
  }

  cierresPaginados(pagina: number, tamano: number, filtros: FiltrosReporte): Observable<Paginado<CierreReporte>> {
    let httpParams = this.params(filtros);
    httpParams = httpParams.set('pagina', pagina.toString()).set('tamano', tamano.toString());
    return this.http.get<Paginado<CierreReporte>>(`${this.apiUrl}/cierres/paginado`, { params: httpParams });
  }

  // Conteos ya agregados en el backend (respeta el alcance del coordinador)
  // para la tarjeta de estadísticas de Inicio, sin traer listados completos.
  // Cacheado (ver OVERVIEW_TTL_MS) para que ir y volver de Inicio no vuelva
  // a pegarle a la red cada vez.
  getConteosOverview(): Observable<ConteosOverview> {
    if (!this.cacheConteosOverview$ || !this.vigente(this.cacheConteosOverviewAt)) {
      this.cacheConteosOverviewAt = Date.now();
      this.cacheConteosOverview$ = this.http.get<ConteosOverview>(`${this.apiUrl}/conteos-overview`).pipe(shareReplay(1));
    }
    return this.cacheConteosOverview$;
  }

  // Distribución por estado (asignaciones) y top de cupos por entidad, ya
  // agregados en el backend (respeta el alcance del coordinador) para los
  // gráficos de Inicio, sin traer los listados completos. Mismo cacheo corto.
  getDistribucionEstados(): Observable<DistribucionEstadoReporte[]> {
    if (!this.cacheDistribucionEstados$ || !this.vigente(this.cacheDistribucionEstadosAt)) {
      this.cacheDistribucionEstadosAt = Date.now();
      this.cacheDistribucionEstados$ = this.http.get<DistribucionEstadoReporte[]>(`${this.apiUrl}/distribucion-estados`).pipe(shareReplay(1));
    }
    return this.cacheDistribucionEstados$;
  }

  getCuposTopEntidades(): Observable<CupoEntidadReporte[]> {
    if (!this.cacheCuposTopEntidades$ || !this.vigente(this.cacheCuposTopEntidadesAt)) {
      this.cacheCuposTopEntidadesAt = Date.now();
      this.cacheCuposTopEntidades$ = this.http.get<CupoEntidadReporte[]>(`${this.apiUrl}/cupos-top-entidades`).pipe(shareReplay(1));
    }
    return this.cacheCuposTopEntidades$;
  }

  exportar(tipo: TipoReporte, filtros: FiltrosReporte): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/exportar`, {
      params: this.params(filtros).set('tipo', tipo),
      responseType: 'blob'
    });
  }

  private params(filtros: FiltrosReporte): HttpParams {
    let params = new HttpParams();
    if (filtros.periodo?.trim()) params = params.set('periodo', filtros.periodo.trim());
    if (filtros.carrera?.trim()) params = params.set('carrera', filtros.carrera.trim());
    if (filtros.entidad?.trim()) params = params.set('entidad', filtros.entidad.trim());
    if (filtros.proceso && filtros.proceso !== 'TODOS') params = params.set('proceso', filtros.proceso);
    return params;
  }
}
