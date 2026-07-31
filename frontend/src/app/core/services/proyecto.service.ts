import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { HttpParams } from '@angular/common/http';
import { Observable, shareReplay } from 'rxjs';
import { Fundacion } from './fundacion.service';
import { Carrera } from './carrera.service';
import { claveCacheSesion } from './cache-sesion';
import { Paginado, paramsPaginado } from './paginacion';

// NO_ESPECIFICADA solo existe en proyectos históricos migrados por la fase 39;
// el backend exige una modalidad real al crear o editar.
export type ModalidadProyecto = 'PRESENCIAL' | 'VIRTUAL' | 'HIBRIDA' | 'NO_ESPECIFICADA';

export function modalidadProyectoTexto(modalidad?: string): string {
  const etiquetas: Record<string, string> = {
    PRESENCIAL: 'Presencial',
    VIRTUAL: 'Virtual',
    HIBRIDA: 'Híbrida'
  };
  return etiquetas[modalidad || ''] || 'Por definir';
}

export interface ProyectoCarrera {
  id?: number;
  carrera: Carrera;
  cuposTotales?: number | null;
  cuposDisponibles?: number | null;
}

export interface Proyecto {
  id?: number;
  fundacion: Fundacion;
  nombre: string;
  descripcion: string;
  cuposTotales?: number;
  cuposDisponibles?: number;
  estado?: boolean;
  periodo: string;
  horasRequeridas: number;
  ciudad: string;
  modalidad: ModalidadProyecto;
  carreras: ProyectoCarrera[];
}

@Injectable({
  providedIn: 'root'
})
export class ProyectoService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/proyectos';

  private cacheAll$: Observable<Proyecto[]> | null = null;
  private lastPeriodo = '';

  getAll(periodo?: string): Observable<Proyecto[]> {
    const key = `${claveCacheSesion()}::${periodo || 'TODOS'}`;
    if (this.cacheAll$ && this.lastPeriodo === key) {
      return this.cacheAll$;
    }
    this.lastPeriodo = key;
    const params = periodo ? new HttpParams().set('periodo', periodo) : undefined;
    this.cacheAll$ = this.http.get<Proyecto[]>(this.apiUrl, { params }).pipe(shareReplay(1));
    return this.cacheAll$;
  }

  private cachePaginado$: Observable<Paginado<Proyecto>> | null = null;
  private lastParamsKey = '';

  // Fase de optimización: listado paginado con filtros resueltos en el backend
  // (mismo patrón que FundacionService.getPaginado).
  getPaginado(pagina: number, tamano: number,
              filtros: { texto?: string; periodo?: string; estado?: string } = {},
              sortBy?: string, sortDir?: string): Observable<Paginado<Proyecto>> {
    const key = JSON.stringify({ sesion: claveCacheSesion(), pagina, tamano, filtros, sortBy, sortDir });
    if (this.cachePaginado$ && this.lastParamsKey === key) {
      return this.cachePaginado$;
    }
    this.lastParamsKey = key;
    this.cachePaginado$ = this.http.get<Paginado<Proyecto>>(`${this.apiUrl}/paginado`, {
      params: paramsPaginado(pagina, tamano, filtros, sortBy, sortDir)
    }).pipe(shareReplay(1));
    return this.cachePaginado$;
  }

  clearCache(): void {
    this.cacheAll$ = null;
    this.lastPeriodo = '';
    this.cachePaginado$ = null;
    this.lastParamsKey = '';
  }

  getById(id: number): Observable<Proyecto> {
    return this.http.get<Proyecto>(`${this.apiUrl}/${id}`);
  }

  getByFundacionId(fundacionId: number): Observable<Proyecto[]> {
    return this.http.get<Proyecto[]>(`${this.apiUrl}/fundacion/${fundacionId}`);
  }

  create(proyecto: Proyecto): Observable<Proyecto> {
    this.clearCache();
    return this.http.post<Proyecto>(this.apiUrl, proyecto);
  }

  update(id: number, proyecto: Proyecto): Observable<Proyecto> {
    this.clearCache();
    return this.http.put<Proyecto>(`${this.apiUrl}/${id}`, proyecto);
  }

  delete(id: number): Observable<void> {
    this.clearCache();
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
