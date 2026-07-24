import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, shareReplay } from 'rxjs';
import { Estudiante } from './estudiante.service';

export interface Asistencia {
  id?: number;
  estudiante?: Estudiante;
  practica?: { id?: number };
  vinculacion?: { id?: number };
  fecha: string;
  horaIngreso?: string;
  horaSalida?: string;
  estado?: 'Presente' | 'Atraso' | 'Falta';
  observaciones?: string;
}

@Injectable({ providedIn: 'root' })
export class AsistenciaService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/asistencias';

  private cacheAll$: Observable<Asistencia[]> | null = null;
  private cacheMine$: Observable<Asistencia[]> | null = null;

  getAll(): Observable<Asistencia[]> {
    if (!this.cacheAll$) {
      this.cacheAll$ = this.http.get<Asistencia[]>(this.apiUrl).pipe(shareReplay(1));
    }
    return this.cacheAll$;
  }

  getMine(): Observable<Asistencia[]> {
    if (!this.cacheMine$) {
      this.cacheMine$ = this.http.get<Asistencia[]>(`${this.apiUrl}/me`).pipe(shareReplay(1));
    }
    return this.cacheMine$;
  }

  getByPractica(practicaId: number): Observable<Asistencia[]> {
    return this.http.get<Asistencia[]>(`${this.apiUrl}/practica/${practicaId}`);
  }

  getByVinculacion(vinculacionId: number): Observable<Asistencia[]> {
    return this.http.get<Asistencia[]>(`${this.apiUrl}/vinculacion/${vinculacionId}`);
  }

  private clearCaches(): void {
    this.cacheAll$ = null;
    this.cacheMine$ = null;
  }

  create(asistencia: Asistencia): Observable<Asistencia> {
    this.clearCaches();
    return this.http.post<Asistencia>(this.apiUrl, asistencia);
  }
}
