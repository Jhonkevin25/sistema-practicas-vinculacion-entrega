import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Usuario } from './usuario.service';

export interface FechaLimiteCalificacion {
  id?: number;
  periodoAcademico: string;
  parcial: number;
  fechaLimite: string;
}

export interface FechasConvocatoria {
  id?: number;
  periodoAcademico: string;
  tipo: 'PRACTICAS' | 'VINCULACION';
  convocatoriaInicio: string;
  convocatoriaFin: string;
  fechaLimiteDocumentos: string;
  fechaInicioPostulacion: string;
  creadoPor?: Usuario;
}

@Injectable({ providedIn: 'root' })
export class ConfiguracionService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/configuracion/fechas';

  getAll(): Observable<FechasConvocatoria[]> {
    return this.http.get<FechasConvocatoria[]>(this.apiUrl);
  }

  getByTipo(tipo: 'PRACTICAS' | 'VINCULACION'): Observable<FechasConvocatoria[]> {
    return this.http.get<FechasConvocatoria[]>(this.apiUrl, { params: { tipo } });
  }

  create(fechas: FechasConvocatoria): Observable<FechasConvocatoria> {
    return this.http.post<FechasConvocatoria>(this.apiUrl, fechas);
  }

  getFechasLimite(): Observable<FechaLimiteCalificacion[]> {
    return this.http.get<FechaLimiteCalificacion[]>('/api/configuracion/fechas-limite');
  }

  saveFechaLimite(fecha: FechaLimiteCalificacion): Observable<FechaLimiteCalificacion> {
    return this.http.post<FechaLimiteCalificacion>('/api/configuracion/fechas-limite', fecha);
  }
}
