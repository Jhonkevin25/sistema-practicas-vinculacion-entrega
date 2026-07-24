import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, shareReplay } from 'rxjs';
import { Usuario } from './usuario.service';
import { Paginado, paramsPaginado } from './paginacion';

export interface Estudiante {
  id?: number;
  usuario: Usuario;
  matricula: string;
  carrera: string;
  semestre?: number;
  periodoAcademico?: string;
}

@Injectable({
  providedIn: 'root'
})
export class EstudianteService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/estudiantes';

  private cachePaginado$: Observable<Paginado<Estudiante>> | null = null;
  private lastParamsKey = '';

  private cacheAll$: Observable<Estudiante[]> | null = null;
  private cacheMe$: Observable<Estudiante> | null = null;

  getAll(): Observable<Estudiante[]> {
    if (!this.cacheAll$) {
      this.cacheAll$ = this.http.get<Estudiante[]>(this.apiUrl).pipe(shareReplay(1));
    }
    return this.cacheAll$;
  }

  // Fase 43: listado paginado con filtros y alcance resueltos en el backend
  getPaginado(pagina: number, tamano: number,
              filtros: { texto?: string; carrera?: string; semestre?: string } = {},
              sortBy?: string, sortDir?: string): Observable<Paginado<Estudiante>> {
    const key = JSON.stringify({ pagina, tamano, filtros, sortBy, sortDir });
    if (this.cachePaginado$ && this.lastParamsKey === key) {
      return this.cachePaginado$;
    }
    this.lastParamsKey = key;
    this.cachePaginado$ = this.http.get<Paginado<Estudiante>>(`${this.apiUrl}/paginado`, {
      params: paramsPaginado(pagina, tamano, filtros, sortBy, sortDir)
    }).pipe(shareReplay(1));
    return this.cachePaginado$;
  }

  clearCache(): void {
    this.cachePaginado$ = null;
    this.cacheAll$ = null;
    this.cacheMe$ = null;
  }

  getMe(): Observable<Estudiante> {
    if (!this.cacheMe$) {
      this.cacheMe$ = this.http.get<Estudiante>(`${this.apiUrl}/me`).pipe(shareReplay(1));
    }
    return this.cacheMe$;
  }

  getById(id: number): Observable<Estudiante> {
    return this.http.get<Estudiante>(`${this.apiUrl}/${id}`);
  }

  create(estudiante: Estudiante): Observable<Estudiante> {
    this.clearCache();
    return this.http.post<Estudiante>(this.apiUrl, estudiante);
  }

  update(id: number, estudiante: Estudiante): Observable<Estudiante> {
    this.clearCache();
    return this.http.put<Estudiante>(`${this.apiUrl}/${id}`, estudiante);
  }

  delete(id: number): Observable<void> {
    this.clearCache();
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
