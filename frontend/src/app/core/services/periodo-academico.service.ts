import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, of, shareReplay, tap } from 'rxjs';
import { contextoValidacionSesion } from '../interceptors/http-context-tokens';

export type EstadoPeriodoAcademico = 'PLANIFICADO' | 'ACTIVO' | 'CERRADO';

export interface PeriodoAcademico {
  id?: number;
  codigo: string;
  fechaInicio: string;
  fechaFin: string;
  estado: EstadoPeriodoAcademico;
}

@Injectable({ providedIn: 'root' })
export class PeriodoAcademicoService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/configuracion/periodos';

  readonly activo = signal<PeriodoAcademico | null>(null);

  // Fase 43: el catálogo de periodos se descarga una sola vez por sesión;
  // toda escritura (crear/editar/eliminar/cerrar) invalida la caché.
  private cache$: Observable<PeriodoAcademico[]> | null = null;

  getAll(): Observable<PeriodoAcademico[]> {
    if (!this.cache$) {
      this.cache$ = this.http.get<PeriodoAcademico[]>(this.apiUrl).pipe(
        tap({ error: () => this.invalidar() }),
        shareReplay(1)
      );
    }
    return this.cache$;
  }

  private invalidar(): void {
    this.cache$ = null;
  }

  // Lo llama AuthService.logout(): la caché y el periodo activo no deben
  // sobrevivir a un cambio de usuario en la misma pestaña
  reset(): void {
    this.invalidar();
    this.activo.set(null);
  }

  cargarActivo(): Observable<PeriodoAcademico | null> {
    return this.http.get<PeriodoAcademico>(`${this.apiUrl}/activo`, {
      context: contextoValidacionSesion()
    }).pipe(
      tap(periodo => this.activo.set(periodo)),
      catchError(() => {
        this.activo.set(null);
        return of(null);
      })
    );
  }

  create(periodo: PeriodoAcademico): Observable<PeriodoAcademico> {
    return this.http.post<PeriodoAcademico>(this.apiUrl, periodo).pipe(tap(() => this.invalidar()));
  }

  update(id: number, periodo: PeriodoAcademico): Observable<PeriodoAcademico> {
    return this.http.put<PeriodoAcademico>(`${this.apiUrl}/${id}`, periodo).pipe(tap(() => this.invalidar()));
  }

  // Fase 42: cierre formal — expira postulaciones pendientes y bloquea
  // nuevas asignaciones del periodo; falla si hay expedientes activos
  cerrar(id: number): Observable<PeriodoAcademico> {
    return this.http.post<PeriodoAcademico>(`${this.apiUrl}/${id}/cerrar`, {}).pipe(tap(() => this.invalidar()));
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`).pipe(tap(() => this.invalidar()));
  }
}
