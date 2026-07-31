import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export type TipoDocumento = 'cv' | 'carta' | 'cedula' | 'carta_aceptacion' | 'informe_final' | 'certificado';

export interface DocumentoRequerido {
  id?: number;
  proceso: 'GENERAL' | 'PRACTICAS' | 'VINCULACION';
  carrera?: string;
  etapa?: string;
  tipoDocumento: TipoDocumento | string;
  nombre: string;
  descripcion?: string;
  obligatorio: boolean;
  momento: 'PRE_POSTULACION' | 'POST_ACEPTACION' | 'CIERRE';
  activo?: boolean;
}

export interface DocEstudiante {
  id?: number;
  estudiante?: {
    id?: number;
    carrera?: string;
    usuario?: {
      nombre?: string;
      apellido?: string;
      email?: string;
    };
  };
  tipoDocumento: TipoDocumento | string;
  urlArchivo?: string | null;
  proceso?: 'GENERAL' | 'PRACTICAS' | 'VINCULACION';
  carrera?: string;
  etapa?: string;
  estado?: string;
  observacion?: string | null;
  fechaRevision?: string;
  fechaSubida?: string;
}

export interface ArchivoDocumentoResponse {
  url: string;
}

// Documentos obligatorios del expediente (tabla DOCS_ESTUDIANTE). El expediente
// y la autorizacion se resuelven desde el JWT en el backend.
@Injectable({ providedIn: 'root' })
export class DocumentoService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/documentos';

  getMisDocumentos(proceso?: 'GENERAL' | 'PRACTICAS' | 'VINCULACION'): Observable<TipoDocumento[]> {
    const options = proceso ? { params: { proceso } } : {};
    return this.http.get<TipoDocumento[]>(`${this.apiUrl}/me`, options);
  }

  getMisDocumentosDetalle(proceso?: 'GENERAL' | 'PRACTICAS' | 'VINCULACION'): Observable<DocEstudiante[]> {
    const options = proceso ? { params: { proceso } } : {};
    return this.http.get<DocEstudiante[]>(`${this.apiUrl}/me/detalle`, options);
  }

  getRequeridos(proceso?: 'GENERAL' | 'PRACTICAS' | 'VINCULACION'): Observable<DocumentoRequerido[]> {
    const options = proceso ? { params: { proceso } } : {};
    return this.http.get<DocumentoRequerido[]>(`${this.apiUrl}/requeridos`, options);
  }

  saveRequerido(requerido: DocumentoRequerido): Observable<DocumentoRequerido> {
    return requerido.id
      ? this.http.put<DocumentoRequerido>(`${this.apiUrl}/requeridos/${requerido.id}`, requerido)
      : this.http.post<DocumentoRequerido>(`${this.apiUrl}/requeridos`, requerido);
  }

  deleteRequerido(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/requeridos/${id}`);
  }

  subir(tipo: TipoDocumento, proceso: 'GENERAL' | 'PRACTICAS' | 'VINCULACION' = 'GENERAL'): Observable<TipoDocumento[]> {
    return this.http.post<TipoDocumento[]>(`${this.apiUrl}/me`, null, {
      params: { tipo, proceso }
    });
  }

  subirArchivo(documentoId: number, archivo: File): Observable<DocEstudiante> {
    const formData = new FormData();
    formData.append('archivo', archivo);
    return this.http.post<DocEstudiante>(`${this.apiUrl}/me/${documentoId}/archivo`, formData);
  }

  getMiArchivo(documentoId: number): Observable<ArchivoDocumentoResponse> {
    return this.http.get<ArchivoDocumentoResponse>(`${this.apiUrl}/me/${documentoId}/archivo`);
  }

  getArchivoRevision(documentoId: number): Observable<ArchivoDocumentoResponse> {
    return this.http.get<ArchivoDocumentoResponse>(`${this.apiUrl}/${documentoId}/archivo`);
  }

  getRevision(filtros?: {
    proceso?: 'GENERAL' | 'PRACTICAS' | 'VINCULACION' | 'TODOS';
    estado?: string;
    carrera?: string;
    estudianteId?: number;
  }): Observable<DocEstudiante[]> {
    const params: Record<string, string> = {};
    if (filtros?.proceso && filtros.proceso !== 'TODOS') params['proceso'] = filtros.proceso;
    if (filtros?.estado && filtros.estado !== 'TODOS') params['estado'] = filtros.estado;
    if (filtros?.carrera && filtros.carrera !== 'TODOS') params['carrera'] = filtros.carrera;
    if (filtros?.estudianteId != null) params['estudianteId'] = String(filtros.estudianteId);
    return this.http.get<DocEstudiante[]>(`${this.apiUrl}/revision`, { params });
  }

  revisar(documentoId: number, estado: 'aprobado' | 'requiere_correccion', observacion = ''): Observable<DocEstudiante> {
    return this.http.put<DocEstudiante>(`${this.apiUrl}/${documentoId}/revision`, { estado, observacion });
  }
}
