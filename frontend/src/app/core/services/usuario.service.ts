import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { Observable, shareReplay } from 'rxjs';
import { Paginado, paramsPaginado } from './paginacion';
import { SKIP_ERROR_TOAST } from '../interceptors/http-context-tokens';

export interface Usuario {
  id?: number;
  cedula?: string;
  nombre: string;
  apellido: string;
  email: string;
  passwordHash?: string;
  activo?: boolean;
  primerLogin?: boolean;
  fuente?: 'MANUAL' | 'CSV_UNIVERSIDAD' | 'API_UNIVERSIDAD';
  externalId?: string;
  // Solo para rol TUTOR: PRACTICAS | VINCULACION | AMBOS
  tutorTipo?: string | null;
  createdAt?: string;
  updatedAt?: string;
  roles?: any[];
}

@Injectable({
  providedIn: 'root'
})
export class UsuarioService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/usuarios';

  private cachePaginado$: Observable<Paginado<Usuario>> | null = null;
  private lastParamsKey = '';

  private cacheAll$: Observable<Usuario[]> | null = null;
  private cacheTutores$: Observable<Usuario[]> | null = null;
  private cacheMe$: Observable<Usuario> | null = null;

  getAll(): Observable<Usuario[]> {
    if (!this.cacheAll$) {
      this.cacheAll$ = this.http.get<Usuario[]>(this.apiUrl).pipe(shareReplay(1));
    }
    return this.cacheAll$;
  }

  // Fase 43: listado paginado con filtros resueltos en el backend
  getPaginado(pagina: number, tamano: number,
              filtros: { texto?: string; rol?: string; activo?: string } = {},
              sortBy?: string, sortDir?: string): Observable<Paginado<Usuario>> {
    const key = JSON.stringify({ pagina, tamano, filtros, sortBy, sortDir });
    if (this.cachePaginado$ && this.lastParamsKey === key) {
      return this.cachePaginado$;
    }
    this.lastParamsKey = key;
    this.cachePaginado$ = this.http.get<Paginado<Usuario>>(`${this.apiUrl}/paginado`, {
      params: paramsPaginado(pagina, tamano, filtros, sortBy, sortDir)
    }).pipe(shareReplay(1));
    return this.cachePaginado$;
  }

  clearCache(): void {
    this.cachePaginado$ = null;
    this.cacheAll$ = null;
    this.cacheTutores$ = null;
    this.cacheMe$ = null;
  }

  // Candidatos para "Nuevo estudiante" (solo ADMIN): cuentas activas con rol
  // ESTUDIANTE y sin expediente. Sin caché: tras crear un estudiante la lista
  // debe reflejar que ese usuario ya no es candidato.
  getCandidatosEstudiante(): Observable<Usuario[]> {
    return this.http.get<Usuario[]>(`${this.apiUrl}/candidatos-estudiante`);
  }

  getTutores(): Observable<Usuario[]> {
    if (!this.cacheTutores$) {
      this.cacheTutores$ = this.http.get<Usuario[]>(`${this.apiUrl}/tutores`).pipe(shareReplay(1));
    }
    return this.cacheTutores$;
  }

  getMe(): Observable<Usuario> {
    if (!this.cacheMe$) {
      this.cacheMe$ = this.http.get<Usuario>(`${this.apiUrl}/me`).pipe(shareReplay(1));
    }
    return this.cacheMe$;
  }

  getById(id: number): Observable<Usuario> {
    return this.http.get<Usuario>(`${this.apiUrl}/${id}`);
  }

  create(usuario: Usuario): Observable<Usuario> {
    this.clearCache();
    return this.http.post<Usuario>(this.apiUrl, usuario, {
      context: new HttpContext().set(SKIP_ERROR_TOAST, true)
    });
  }

  update(id: number, usuario: Usuario): Observable<Usuario> {
    this.clearCache();
    return this.http.put<Usuario>(`${this.apiUrl}/${id}`, usuario, {
      context: new HttpContext().set(SKIP_ERROR_TOAST, true)
    });
  }

  desactivar(id: number): Observable<void> {
    this.clearCache();
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
