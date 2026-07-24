import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface TutorEmpresaCarrera {
  id: number;
  nombre: string;
}

export interface TutorEmpresa {
  id: number;
  tutorId: number | null;
  nombre: string;
  apellido: string;
  email: string | null;
  empresaId: number;
  empresa: string;
  cargo: string | null;
  activo: boolean;
  carreras: TutorEmpresaCarrera[];
}

export interface TutorEmpresaPayload {
  tutorId: number | null;
  empresaId: number | null;
  cargo: string;
  activo: boolean;
  carreraIds: number[];
}

@Injectable({ providedIn: 'root' })
export class TutorEmpresaService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/tutores-empresa';

  getPorEmpresa(empresaId: number): Observable<TutorEmpresa[]> {
    return this.http.get<TutorEmpresa[]>(`${this.apiUrl}/empresa/${empresaId}`);
  }

  getCompatibles(empresaId: number, carrera: string): Observable<TutorEmpresa[]> {
    const params = new HttpParams()
      .set('empresaId', empresaId)
      .set('carrera', carrera);
    return this.http.get<TutorEmpresa[]>(`${this.apiUrl}/compatibles`, { params });
  }

  create(payload: TutorEmpresaPayload): Observable<TutorEmpresa> {
    return this.http.post<TutorEmpresa>(this.apiUrl, payload);
  }

  update(id: number, payload: TutorEmpresaPayload): Observable<TutorEmpresa> {
    return this.http.put<TutorEmpresa>(`${this.apiUrl}/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
