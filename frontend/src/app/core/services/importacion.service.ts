import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Paginado } from './paginacion';

export type TipoImportacion = 'ESTUDIANTES' | 'NOTAS';

export interface ErrorFilaImportacion {
  fila: number;
  identificador: string;
  error: string;
}

export interface ResumenImportacion {
  importacionId: number;
  archivo: string;
  tipo: TipoImportacion;
  estado: 'COMPLETADA' | 'CON_ERRORES' | 'FALLIDA';
  filasTotal: number;
  filasOk: number;
  filasError: number;
  creados: number;
  actualizados: number;
  enlazados: number;
  errores: ErrorFilaImportacion[];
}

export interface Importacion {
  id: number;
  archivoNombre: string;
  tipo: TipoImportacion;
  filasTotal: number;
  filasOk: number;
  filasError: number;
  creados: number;
  actualizados: number;
  enlazados: number;
  estado: 'COMPLETADA' | 'CON_ERRORES' | 'FALLIDA';
  fecha: string;
  detalleErrores: string;
}

@Injectable({ providedIn: 'root' })
export class ImportacionService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/importaciones';

  importarEstudiantes(archivo: File): Observable<ResumenImportacion> {
    return this.importar('estudiantes', archivo);
  }

  importarNotas(archivo: File): Observable<ResumenImportacion> {
    return this.importar('notas', archivo);
  }

  obtenerHistorial(page: number, size: number, tipo?: string, estado?: string): Observable<Paginado<Importacion>> {
    let url = `${this.apiUrl}/paginado?page=${page}&size=${size}`;
    if (tipo) url += `&tipo=${tipo}`;
    if (estado) url += `&estado=${estado}`;
    return this.http.get<Paginado<Importacion>>(url);
  }

  private importar(tipo: 'estudiantes' | 'notas', archivo: File): Observable<ResumenImportacion> {
    const formData = new FormData();
    formData.append('archivo', archivo, archivo.name);
    return this.http.post<ResumenImportacion>(`${this.apiUrl}/${tipo}`, formData);
  }
}
