import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, shareReplay } from 'rxjs';
import { Carrera } from './carrera.service';
import { Fundacion } from './fundacion.service';
import { claveCacheSesion } from './cache-sesion';

export type DistribucionCuposFundacion = 'GENERAL' | 'POR_CARRERA';

export interface OfertaCuposFundacionCarrera {
  id?: number;
  carrera: Carrera;
  cupos: number;
  cuposReservados?: number;
  cuposOcupados?: number;
  cuposDisponibles?: number;
}

export interface OfertaCuposFundacion {
  id?: number;
  fundacion: Fundacion;
  periodoAcademico: string;
  distribucion: DistribucionCuposFundacion;
  cuposTotales: number;
  activo: boolean;
  observacion?: string | null;
  carreras: OfertaCuposFundacionCarrera[];
  cuposReservados?: number;
  cuposOcupados?: number;
  cuposDisponibles?: number;
}

@Injectable({ providedIn: 'root' })
export class OfertaCuposFundacionService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/ofertas-cupos-fundacion';

  private cacheAll$: Observable<OfertaCuposFundacion[]> | null = null;
  private lastPeriodo = '';

  getAll(periodoAcademico?: string): Observable<OfertaCuposFundacion[]> {
    const key = `${claveCacheSesion()}::${periodoAcademico || 'TODOS'}`;
    if (this.cacheAll$ && this.lastPeriodo === key) {
      return this.cacheAll$;
    }
    this.lastPeriodo = key;
    const params = periodoAcademico
      ? new HttpParams().set('periodoAcademico', periodoAcademico)
      : undefined;
    this.cacheAll$ = this.http.get<OfertaCuposFundacion[]>(this.apiUrl, { params }).pipe(shareReplay(1));
    return this.cacheAll$;
  }

  clearCache(): void {
    this.cacheAll$ = null;
    this.lastPeriodo = '';
  }

  create(oferta: OfertaCuposFundacion): Observable<OfertaCuposFundacion> {
    this.clearCache();
    return this.http.post<OfertaCuposFundacion>(this.apiUrl, oferta);
  }

  update(id: number, oferta: OfertaCuposFundacion): Observable<OfertaCuposFundacion> {
    this.clearCache();
    return this.http.put<OfertaCuposFundacion>(`${this.apiUrl}/${id}`, oferta);
  }

  delete(id: number): Observable<void> {
    this.clearCache();
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
