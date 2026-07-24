import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, shareReplay } from 'rxjs';
import { Empresa } from './empresa.service';
import { Fundacion } from './fundacion.service';
import { claveCacheSesion } from './cache-sesion';

export type EstadoConvenio = 'VIGENTE' | 'SUSPENDIDO' | 'FINALIZADO';

export interface Convenio {
  id?: number;
  codigo: string;
  empresa?: Empresa | null;
  fundacion?: Fundacion | null;
  fechaInicio: string;
  fechaFin: string;
  estado: EstadoConvenio;
  urlDocumento?: string | null;
  cuposPactados: number;
  carreras: string[];
  vigente?: boolean;
}

@Injectable({ providedIn: 'root' })
export class ConvenioService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/convenios';

  private cacheAll$: Observable<Convenio[]> | null = null;
  private lastSessionKey = '';

  getAll(): Observable<Convenio[]> {
    const sessionKey = claveCacheSesion();
    if (!this.cacheAll$ || this.lastSessionKey !== sessionKey) {
      this.lastSessionKey = sessionKey;
      this.cacheAll$ = this.http.get<Convenio[]>(this.apiUrl).pipe(shareReplay(1));
    }
    return this.cacheAll$;
  }

  clearCache(): void {
    this.cacheAll$ = null;
    this.lastSessionKey = '';
  }

  getByEmpresa(empresaId: number): Observable<Convenio[]> {
    return this.http.get<Convenio[]>(`${this.apiUrl}/empresa/${empresaId}`);
  }

  getByFundacion(fundacionId: number): Observable<Convenio[]> {
    return this.http.get<Convenio[]>(`${this.apiUrl}/fundacion/${fundacionId}`);
  }

  create(convenio: Convenio): Observable<Convenio> {
    this.clearCache();
    return this.http.post<Convenio>(this.apiUrl, convenio);
  }

  update(id: number, convenio: Convenio): Observable<Convenio> {
    this.clearCache();
    return this.http.put<Convenio>(`${this.apiUrl}/${id}`, convenio);
  }

  delete(id: number): Observable<void> {
    this.clearCache();
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
