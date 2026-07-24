import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import type { Practica } from './practica.service';
import type { Vinculacion } from './vinculacion.service';

export type ProcesoLineaTiempo = 'PRACTICAS' | 'VINCULACION';
export type EstadoFinalizacion = 'REPROBADO' | 'RETIRADO';

export interface EventoLineaTiempo {
  clave: string;
  tipo: string;
  titulo: string;
  descripcion: string;
  fecha: string;
  estado?: string;
  actor?: string;
}

export interface LineaTiempoExpediente {
  proceso: ProcesoLineaTiempo;
  expedienteId: number;
  periodoAcademico: string;
  estado: string;
  motivoFinalizacion?: string;
  finalizadoEn?: string;
  eventos: EventoLineaTiempo[];
}

export interface FinalizacionExcepcional {
  estado: EstadoFinalizacion;
  motivo: string;
}

export function estadoExpedienteTexto(estado?: string | null): string {
  const textos: Record<string, string> = {
    pendiente: 'Pendiente',
    en_curso: 'En curso',
    completado: 'Completado',
    reprobado: 'Reprobado',
    retirado: 'Retirado'
  };
  const clave = String(estado || '').trim().toLowerCase();
  return textos[clave] || estado || 'Sin estado';
}

@Injectable({
  providedIn: 'root'
})
export class LineaTiempoService {
  private readonly http = inject(HttpClient);
  private readonly practicasUrl = '/api/practicas';
  private readonly vinculacionUrl = '/api/vinculacion';

  getPractica(id: number): Observable<LineaTiempoExpediente> {
    return this.http.get<LineaTiempoExpediente>(`${this.practicasUrl}/${id}/linea-tiempo`);
  }

  getVinculacion(id: number): Observable<LineaTiempoExpediente> {
    return this.http.get<LineaTiempoExpediente>(`${this.vinculacionUrl}/${id}/linea-tiempo`);
  }

  getPracticaMe(): Observable<LineaTiempoExpediente[]> {
    return this.http.get<LineaTiempoExpediente[]>(`${this.practicasUrl}/me/linea-tiempo`);
  }

  getVinculacionMe(): Observable<LineaTiempoExpediente[]> {
    return this.http.get<LineaTiempoExpediente[]>(`${this.vinculacionUrl}/me/linea-tiempo`);
  }

  finalizarPractica(id: number, payload: FinalizacionExcepcional): Observable<Practica> {
    return this.http.post<Practica>(`${this.practicasUrl}/${id}/finalizar-excepcional`, payload);
  }

  finalizarVinculacion(id: number, payload: FinalizacionExcepcional): Observable<Vinculacion> {
    return this.http.post<Vinculacion>(`${this.vinculacionUrl}/${id}/finalizar-excepcional`, payload);
  }
}
