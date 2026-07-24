import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, shareReplay } from 'rxjs';
import { Paginado, paramsPaginado } from './paginacion';
import { claveCacheSesion } from './cache-sesion';

export interface Empresa {
  id?: number;
  ruc: string;
  nombre: string;
  direccion?: string;
  cuposDisponibles?: number;
  activo?: boolean;
  tieneConvenio?: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class EmpresaService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/empresas';

  private cachePaginado$: Observable<Paginado<Empresa>> | null = null;
  private lastParamsKey = '';

  getAll(): Observable<Empresa[]> {
    return this.http.get<Empresa[]>(this.apiUrl);
  }

  // Fase 43: listado paginado con filtros resueltos en el backend
  getPaginado(pagina: number, tamano: number,
              filtros: { texto?: string; activo?: string } = {},
              sortBy?: string, sortDir?: string): Observable<Paginado<Empresa>> {
    const key = JSON.stringify({ sesion: claveCacheSesion(), pagina, tamano, filtros, sortBy, sortDir });
    if (this.cachePaginado$ && this.lastParamsKey === key) {
      return this.cachePaginado$;
    }
    this.lastParamsKey = key;
    this.cachePaginado$ = this.http.get<Paginado<Empresa>>(`${this.apiUrl}/paginado`, {
      params: paramsPaginado(pagina, tamano, filtros, sortBy, sortDir)
    }).pipe(shareReplay(1));
    return this.cachePaginado$;
  }

  clearCache(): void {
    this.cachePaginado$ = null;
    this.lastParamsKey = '';
  }

  getById(id: number): Observable<Empresa> {
    return this.http.get<Empresa>(`${this.apiUrl}/${id}`);
  }

  create(empresa: Empresa): Observable<Empresa> {
    this.clearCache();
    return this.http.post<Empresa>(this.apiUrl, empresa);
  }

  update(id: number, empresa: Empresa): Observable<Empresa> {
    this.clearCache();
    return this.http.put<Empresa>(`${this.apiUrl}/${id}`, empresa);
  }

  delete(id: number): Observable<void> {
    this.clearCache();
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
