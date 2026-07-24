import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

// Forma en que el backend serializa la entidad Auditoria
interface AuditoriaApi {
  id: number;
  tablaAfectada: string;
  accion: string;
  datosAntes?: string;
  datosDespues?: string;
  usuario?: { nombre: string; apellido: string } | null;
  fecha: string;
}

// Forma de presentacion que usan las pantallas
export interface RegistroAuditoria {
  id: number;
  fecha: string;
  usuario: string;
  accion: string;
  detalle: string;
}

@Injectable({ providedIn: 'root' })
export class AuditoriaService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/auditoria';

  getByTabla(tabla: string): Observable<RegistroAuditoria[]> {
    return this.http.get<AuditoriaApi[]>(this.apiUrl, { params: { tabla } }).pipe(
      map(rows => rows.map(r => this.toRegistro(r)))
    );
  }

  private toRegistro(r: AuditoriaApi): RegistroAuditoria {
    let detalle = '';
    try {
      detalle = JSON.parse(r.datosDespues || '{}').detalle || '';
    } catch {
      detalle = r.datosDespues || '';
    }
    return {
      id: r.id,
      fecha: r.fecha ? new Date(r.fecha).toLocaleString() : '',
      usuario: r.usuario ? `${r.usuario.nombre} ${r.usuario.apellido}` : 'Sistema',
      accion: r.accion,
      detalle
    };
  }
}
