import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SKIP_ERROR_TOAST } from '../interceptors/http-context-tokens';

export type TipoNotificacion =
  | 'documento_aprobado'
  | 'documento_rechazado'
  | 'postulacion_enviada'
  | 'postulacion_resuelta'
  | 'asignacion_practica'
  | 'asignacion_vinculacion'
  | 'bitacora_rechazada'
  | 'bitacora_requiere_correccion'
  | 'nota_registrada'
  | 'nota_coordinacion'
  | 'expediente_atrasado'
  | 'encuesta_habilitada'
  | 'expediente_por_cerrar'
  | 'expediente_cerrado';

export interface Notificacion {
  id: number;
  tipo: TipoNotificacion | string;
  titulo: string;
  mensaje: string;
  leida: boolean;
  fechaCreacion: string;
  referenciaTipo?: string;
  referenciaId?: number;
  link?: string;
}

// Notificaciones internas del usuario autenticado; el backend resuelve el
// destinatario desde el JWT (/me), nunca desde un id del cliente.
@Injectable({ providedIn: 'root' })
export class NotificacionService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/notificaciones';

  getMisNotificaciones(): Observable<Notificacion[]> {
    return this.http.get<Notificacion[]>(`${this.apiUrl}/me`);
  }

  // Carga silenciosa (polling): omite el toast global si falla.
  getNoLeidasCount(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.apiUrl}/me/no-leidas/count`, {
      context: new HttpContext().set(SKIP_ERROR_TOAST, true)
    });
  }

  marcarLeida(id: number): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/${id}/leida`, null);
  }

  marcarTodasLeidas(): Observable<{ count: number }> {
    return this.http.put<{ count: number }>(`${this.apiUrl}/me/leidas`, null);
  }
}
