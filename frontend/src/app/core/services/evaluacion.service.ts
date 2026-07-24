import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Practica } from './practica.service';
import { Vinculacion } from './vinculacion.service';

export interface EvaluacionDetalle {
  id?: number;
  practica?: Practica;
  vinculacion?: Vinculacion;
  parcial: number;
  notaTutor?: number;
  notaCoord?: number;
  notaFinal?: number;
  encuestaCompletada?: boolean;
}

export interface EncuestaSatisfaccionPayload {
  satisfaccionTutor: number;
  satisfaccionEmpresaProyecto: number;
  relacionCarrera: number;
  claridadInstrucciones: number;
  comentario?: string;
}

export interface EncuestaSatisfaccion extends EncuestaSatisfaccionPayload {
  id?: number;
  parcial: number;
  fechaEnvio?: string;
}

@Injectable({ providedIn: 'root' })
export class EvaluacionService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/evaluaciones';

  getAll(): Observable<EvaluacionDetalle[]> {
    return this.http.get<EvaluacionDetalle[]>(this.apiUrl);
  }

  getByPractica(practicaId: number): Observable<EvaluacionDetalle[]> {
    return this.http.get<EvaluacionDetalle[]>(`${this.apiUrl}/practica/${practicaId}`);
  }

  getByVinculacion(vinculacionId: number): Observable<EvaluacionDetalle[]> {
    return this.http.get<EvaluacionDetalle[]>(`${this.apiUrl}/vinculacion/${vinculacionId}`);
  }

  getEncuestasByPractica(practicaId: number): Observable<EncuestaSatisfaccion[]> {
    return this.http.get<EncuestaSatisfaccion[]>(`${this.apiUrl}/encuesta/practica/${practicaId}`);
  }

  getEncuestasByVinculacion(vinculacionId: number): Observable<EncuestaSatisfaccion[]> {
    return this.http.get<EncuestaSatisfaccion[]>(`${this.apiUrl}/encuesta/vinculacion/${vinculacionId}`);
  }

  // Upsert por (practica, parcial); enviar solo la nota del rol que califica
  guardar(evaluacion: EvaluacionDetalle): Observable<EvaluacionDetalle> {
    return this.http.post<EvaluacionDetalle>(this.apiUrl, evaluacion);
  }

  marcarEncuesta(
    practicaId: number,
    parcial: number,
    encuesta: EncuestaSatisfaccionPayload
  ): Observable<EvaluacionDetalle> {
    return this.http.post<EvaluacionDetalle>(
      `${this.apiUrl}/encuesta?practicaId=${practicaId}&parcial=${parcial}`, encuesta);
  }

  marcarEncuestaVinculacion(
    vinculacionId: number,
    parcial: number,
    encuesta: EncuestaSatisfaccionPayload
  ): Observable<EvaluacionDetalle> {
    return this.http.post<EvaluacionDetalle>(
      `${this.apiUrl}/encuesta?vinculacionId=${vinculacionId}&parcial=${parcial}`, encuesta);
  }
}
