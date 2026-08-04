import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface TutorFundacionCarrera {
  id: number;
  nombre: string;
}

export interface TutorFundacion {
  id: number;
  tutorId: number | null;
  nombre: string;
  apellido: string;
  email: string | null;
  fundacionId: number;
  fundacion: string;
  cargo: string | null;
  activo: boolean;
  carreras: TutorFundacionCarrera[];
}

export interface TutorFundacionPayload {
  tutorId: number | null;
  fundacionId: number | null;
  cargo: string;
  activo: boolean;
  carreraIds: number[];
}

@Injectable({ providedIn: 'root' })
export class TutorFundacionService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/tutores-fundacion';

  getPorFundacion(fundacionId: number): Observable<TutorFundacion[]> {
    return this.http.get<TutorFundacion[]>(`${this.apiUrl}/fundacion/${fundacionId}`);
  }

  getCompatibles(fundacionId: number, carrera: string): Observable<TutorFundacion[]> {
    const params = new HttpParams()
      .set('fundacionId', fundacionId)
      .set('carrera', carrera);
    return this.http.get<TutorFundacion[]>(`${this.apiUrl}/compatibles`, { params });
  }

  create(payload: TutorFundacionPayload): Observable<TutorFundacion> {
    return this.http.post<TutorFundacion>(this.apiUrl, payload);
  }

  update(id: number, payload: TutorFundacionPayload): Observable<TutorFundacion> {
    return this.http.put<TutorFundacion>(`${this.apiUrl}/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
