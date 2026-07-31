import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, shareReplay } from 'rxjs';
import { Estudiante } from './estudiante.service';
import { Practica } from './practica.service';
import { Vinculacion } from './vinculacion.service';

export interface Bitacora {
  id?: number;
  estudiante: Estudiante;
  practica?: Practica;
  vinculacion?: Vinculacion;
  parcial: 1 | 2 | 3;
  fecha: string;
  actividad: string;
  horas: number;
  horasExtra?: number;
  fechaRegistro?: string;
  observaciones?: string;
  observacionesTutor?: string;
  estado?: 'pendiente' | 'aprobada' | 'rechazada' | 'requiere_correccion';
}

export interface ReenvioBitacora {
  fecha: string;
  actividad: string;
  horas: number;
  observaciones?: string;
}

export function correccionBitacoraPendiente(bitacora: Bitacora, historial: Bitacora[]): boolean {
  if (bitacora.estado !== 'requiere_correccion') return false;
  return !historial.some(otra =>
    otra.estado === 'aprobada'
    && otra.parcial === bitacora.parcial
    && !!otra.id && !!bitacora.id
    && otra.id > bitacora.id);
}

@Injectable({ providedIn: 'root' })
export class BitacoraService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/bitacoras';

  private cacheAll$: Observable<Bitacora[]> | null = null;
  private cacheMine$: Observable<Bitacora[]> | null = null;

  getAll(forceRefresh = false): Observable<Bitacora[]> {
    if (forceRefresh || !this.cacheAll$) {
      this.cacheAll$ = this.http.get<Bitacora[]>(this.apiUrl).pipe(shareReplay(1));
    }
    return this.cacheAll$;
  }

  getMine(): Observable<Bitacora[]> {
    if (!this.cacheMine$) {
      this.cacheMine$ = this.http.get<Bitacora[]>(`${this.apiUrl}/me`).pipe(shareReplay(1));
    }
    return this.cacheMine$;
  }

  getByEstudiante(estudianteId: number): Observable<Bitacora[]> {
    return this.http.get<Bitacora[]>(`${this.apiUrl}/estudiante/${estudianteId}`);
  }

  private clearCaches(): void {
    this.cacheAll$ = null;
    this.cacheMine$ = null;
  }

  create(bitacora: Bitacora): Observable<Bitacora> {
    this.clearCaches();
    return this.http.post<Bitacora>(this.apiUrl, bitacora);
  }

  update(id: number, bitacora: Partial<Bitacora>): Observable<Bitacora> {
    this.clearCaches();
    return this.http.put<Bitacora>(`${this.apiUrl}/${id}`, bitacora);
  }

  reenviar(id: number, bitacora: ReenvioBitacora): Observable<Bitacora> {
    this.clearCaches();
    return this.http.put<Bitacora>(`${this.apiUrl}/${id}/reenviar`, bitacora);
  }
}
