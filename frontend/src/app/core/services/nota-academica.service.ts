import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Estudiante } from './estudiante.service';
import { Usuario } from './usuario.service';

export interface NotaAcademica {
  id?: number;
  estudiante: Estudiante;
  carrera?: string;
  semestre: number;
  periodoAcademico: string;
  promedio: number;
  documentoUrl?: string;
  fuente?: 'MANUAL' | 'CSV' | 'API_UNIVERSIDAD';
  externalId?: string;
  emailInstitucional?: string;
  estado?: 'PENDIENTE' | 'VERIFICADO' | 'RECHAZADO';
  observaciones?: string;
  verificadoPor?: Usuario;
  fechaVerificacion?: string;
  createdAt?: string;
  updatedAt?: string;
}

@Injectable({
  providedIn: 'root'
})
export class NotaAcademicaService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/notas-academicas';

  getAll(): Observable<NotaAcademica[]> {
    return this.http.get<NotaAcademica[]>(this.apiUrl);
  }

  getMisNotas(): Observable<NotaAcademica[]> {
    return this.http.get<NotaAcademica[]>(`${this.apiUrl}/me`);
  }

  create(nota: Partial<NotaAcademica>): Observable<NotaAcademica> {
    return this.http.post<NotaAcademica>(this.apiUrl, nota);
  }

  verificar(id: number, estado: 'VERIFICADO' | 'RECHAZADO', observaciones = ''): Observable<NotaAcademica> {
    return this.http.put<NotaAcademica>(`${this.apiUrl}/${id}`, { estado, observaciones });
  }
}
