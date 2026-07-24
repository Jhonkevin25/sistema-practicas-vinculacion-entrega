import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, shareReplay } from 'rxjs';
import { Carrera } from './carrera.service';
import { Empresa } from './empresa.service';
import { claveCacheSesion } from './cache-sesion';

export type DistribucionCuposEmpresa = 'GENERAL' | 'POR_CARRERA';

export interface OfertaCuposEmpresaCarrera {
  id?: number;
  carrera: Carrera;
  cupos: number;
  cuposReservados?: number;
  cuposOcupados?: number;
  cuposDisponibles?: number;
}

export interface OfertaCuposEmpresa {
  id?: number;
  empresa: Empresa;
  periodoAcademico: string;
  distribucion: DistribucionCuposEmpresa;
  cuposTotales: number;
  activo: boolean;
  observacion?: string | null;
  carreras: OfertaCuposEmpresaCarrera[];
  cuposReservados?: number;
  cuposOcupados?: number;
  cuposDisponibles?: number;
}

@Injectable({ providedIn: 'root' })
export class OfertaCuposEmpresaService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/ofertas-cupos-empresa';

  private cacheAll$: Observable<OfertaCuposEmpresa[]> | null = null;
  private lastPeriodo = '';

  getAll(periodoAcademico?: string): Observable<OfertaCuposEmpresa[]> {
    const key = `${claveCacheSesion()}::${periodoAcademico || 'TODOS'}`;
    if (this.cacheAll$ && this.lastPeriodo === key) {
      return this.cacheAll$;
    }
    this.lastPeriodo = key;
    const params = periodoAcademico
      ? new HttpParams().set('periodoAcademico', periodoAcademico)
      : undefined;
    this.cacheAll$ = this.http.get<OfertaCuposEmpresa[]>(this.apiUrl, { params }).pipe(shareReplay(1));
    return this.cacheAll$;
  }

  clearCache(): void {
    this.cacheAll$ = null;
    this.lastPeriodo = '';
  }

  create(oferta: OfertaCuposEmpresa): Observable<OfertaCuposEmpresa> {
    this.clearCache();
    return this.http.post<OfertaCuposEmpresa>(this.apiUrl, oferta);
  }

  update(id: number, oferta: OfertaCuposEmpresa): Observable<OfertaCuposEmpresa> {
    this.clearCache();
    return this.http.put<OfertaCuposEmpresa>(`${this.apiUrl}/${id}`, oferta);
  }

  delete(id: number): Observable<void> {
    this.clearCache();
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
