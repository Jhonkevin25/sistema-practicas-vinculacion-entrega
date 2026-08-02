import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, shareReplay } from 'rxjs';
import { Estudiante } from './estudiante.service';
import { Empresa } from './empresa.service';
import { Practica } from './practica.service';
import { VacantePractica } from './vacante.service';

export interface PostulacionMeritocratica {
  id?: number;
  estudiante: Estudiante;
  promedio?: number;
  pref1: VacantePractica;
  pref2: VacantePractica;
  pref3?: VacantePractica;
  score?: number;
  estado?: 'Pendiente' | 'Procesado' | 'Aprobado' | 'Rechazado';
  asignadoEmpresa?: Empresa;
  asignadoVacante?: VacantePractica;
  ajustadoManualmente?: boolean;
  justificacionAjuste?: string;
}

@Injectable({ providedIn: 'root' })
export class PostulacionService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/postulaciones';

  private cacheAll$: Observable<PostulacionMeritocratica[]> | null = null;
  private cacheMine$: Observable<PostulacionMeritocratica[]> | null = null;

  getAll(): Observable<PostulacionMeritocratica[]> {
    if (!this.cacheAll$) {
      this.cacheAll$ = this.http.get<PostulacionMeritocratica[]>(this.apiUrl).pipe(shareReplay(1));
    }
    return this.cacheAll$;
  }

  getMine(): Observable<PostulacionMeritocratica[]> {
    if (!this.cacheMine$) {
      this.cacheMine$ = this.http.get<PostulacionMeritocratica[]>(`${this.apiUrl}/me`).pipe(shareReplay(1));
    }
    return this.cacheMine$;
  }

  clearCaches(): void {
    this.cacheAll$ = null;
    this.cacheMine$ = null;
  }

  create(postulacion: PostulacionMeritocratica): Observable<PostulacionMeritocratica> {
    this.clearCaches();
    return this.http.post<PostulacionMeritocratica>(this.apiUrl, postulacion);
  }

  update(id: number, postulacion: PostulacionMeritocratica): Observable<PostulacionMeritocratica> {
    this.clearCaches();
    return this.http.put<PostulacionMeritocratica>(`${this.apiUrl}/${id}`, postulacion);
  }

  procesarMeritocracia(): Observable<PostulacionMeritocratica[]> {
    this.clearCaches();
    return this.http.post<PostulacionMeritocratica[]>(`${this.apiUrl}/procesar-meritocracia`, {});
  }

  consolidarPractica(id: number, practica: Practica): Observable<Practica> {
    this.clearCaches();
    return this.http.post<Practica>(`${this.apiUrl}/${id}/consolidar-practica`, practica);
  }
}
