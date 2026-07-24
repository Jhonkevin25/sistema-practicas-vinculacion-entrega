import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Estudiante } from './estudiante.service';
import { Practica } from './practica.service';
import { Usuario } from './usuario.service';
import { Vinculacion } from './vinculacion.service';

export interface NotaCoordinacion {
  id?: number;
  estudiante: Estudiante;
  practica?: Practica;
  vinculacion?: Vinculacion;
  autor?: Usuario;
  mensaje: string;
  fechaCreacion?: string;
}

// Constancia libre del coordinador (o admin) por expediente, append-only:
// solo hay creacion y lectura, sin editar/eliminar.
@Injectable({ providedIn: 'root' })
export class NotaCoordinacionService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/notas-coordinacion';

  getMisNotas(): Observable<NotaCoordinacion[]> {
    return this.http.get<NotaCoordinacion[]>(`${this.apiUrl}/me`);
  }

  getByEstudiante(estudianteId: number): Observable<NotaCoordinacion[]> {
    return this.http.get<NotaCoordinacion[]>(`${this.apiUrl}/estudiante/${estudianteId}`);
  }

  crear(nota: { estudianteId: number; practicaId?: number; vinculacionId?: number; mensaje: string }): Observable<NotaCoordinacion> {
    const payload: any = {
      estudiante: { id: nota.estudianteId },
      mensaje: nota.mensaje
    };
    if (nota.practicaId) payload.practica = { id: nota.practicaId };
    if (nota.vinculacionId) payload.vinculacion = { id: nota.vinculacionId };
    return this.http.post<NotaCoordinacion>(this.apiUrl, payload);
  }
}
