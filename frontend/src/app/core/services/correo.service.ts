import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Paginado } from './paginacion';
export interface CorreoCola {
  id: number;
  destinatario: string;
  asunto: string;
  cuerpoHtml: string;
  estado: 'PENDIENTE' | 'ENVIADO' | 'FALLIDO';
  intentos: number;
  ultimoError: string | null;
  fechaCreacion: string;
  fechaActualizacion: string;
}

@Injectable({ providedIn: 'root' })
export class CorreoService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/correos';

  obtenerPaginado(page: number, size: number, estado?: string, destinatario?: string): Observable<Paginado<CorreoCola>> {
    let url = `${this.apiUrl}/paginado?page=${page}&size=${size}`;
    if (estado) url += `&estado=${estado}`;
    if (destinatario) url += `&destinatario=${destinatario}`;
    return this.http.get<Paginado<CorreoCola>>(url);
  }

  reintentar(id: number): Observable<CorreoCola> {
    return this.http.post<CorreoCola>(`${this.apiUrl}/${id}/reintentar`, {});
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
