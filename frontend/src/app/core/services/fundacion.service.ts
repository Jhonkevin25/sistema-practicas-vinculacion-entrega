import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, shareReplay } from 'rxjs';
import { Paginado, paramsPaginado } from './paginacion';
import { claveCacheSesion } from './cache-sesion';

export interface Fundacion {
  id?: number;
  ruc: string;
  nombre: string;
  mision?: string;
  areaIntervencion?: string;
  activa?: boolean;
  tieneConvenio?: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class FundacionService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/fundaciones';

  private cachePaginado$: Observable<Paginado<Fundacion>> | null = null;
  private lastParamsKey = '';

  getAll(): Observable<Fundacion[]> {
    return this.http.get<Fundacion[]>(this.apiUrl);
  }

  // Fase 43: listado paginado con filtros resueltos en el backend
  getPaginado(pagina: number, tamano: number,
              filtros: { texto?: string; activa?: string } = {},
              sortBy?: string, sortDir?: string): Observable<Paginado<Fundacion>> {
    const key = JSON.stringify({ sesion: claveCacheSesion(), pagina, tamano, filtros, sortBy, sortDir });
    if (this.cachePaginado$ && this.lastParamsKey === key) {
      return this.cachePaginado$;
    }
    this.lastParamsKey = key;
    this.cachePaginado$ = this.http.get<Paginado<Fundacion>>(`${this.apiUrl}/paginado`, {
      params: paramsPaginado(pagina, tamano, filtros, sortBy, sortDir)
    }).pipe(shareReplay(1));
    return this.cachePaginado$;
  }

  clearCache(): void {
    this.cachePaginado$ = null;
    this.lastParamsKey = '';
  }

  getById(id: number): Observable<Fundacion> {
    return this.http.get<Fundacion>(`${this.apiUrl}/${id}`);
  }

  create(fundacion: Fundacion): Observable<Fundacion> {
    this.clearCache();
    return this.http.post<Fundacion>(this.apiUrl, fundacion);
  }

  update(id: number, fundacion: Fundacion): Observable<Fundacion> {
    this.clearCache();
    return this.http.put<Fundacion>(`${this.apiUrl}/${id}`, fundacion);
  }

  delete(id: number): Observable<void> {
    this.clearCache();
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
