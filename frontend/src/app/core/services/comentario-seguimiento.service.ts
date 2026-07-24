import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export type ProcesoComentario = 'PRACTICAS' | 'VINCULACION';
export type AudienciaComentario = 'ESTUDIANTE' | 'TUTOR' | 'COORDINACION' | 'TODOS';

export interface ComentarioSeguimiento {
  id: number;
  practicaId?: number | null;
  vinculacionId?: number | null;
  autorId: number;
  autor: string;
  autorRol: string;
  audiencia: AudienciaComentario;
  mensaje: string;
  fechaCreacion: string;
}

export interface NuevoComentarioSeguimiento {
  practicaId?: number | null;
  vinculacionId?: number | null;
  audiencia: AudienciaComentario;
  mensaje: string;
}

@Injectable({ providedIn: 'root' })
export class ComentarioSeguimientoService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/comentarios-seguimiento';

  getByExpediente(proceso: ProcesoComentario, expedienteId: number): Observable<ComentarioSeguimiento[]> {
    const recurso = proceso === 'PRACTICAS' ? 'practica' : 'vinculacion';
    return this.http.get<ComentarioSeguimiento[]>(`${this.apiUrl}/${recurso}/${expedienteId}`);
  }

  create(comentario: NuevoComentarioSeguimiento): Observable<ComentarioSeguimiento> {
    return this.http.post<ComentarioSeguimiento>(this.apiUrl, comentario);
  }
}
