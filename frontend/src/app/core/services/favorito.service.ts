import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

// El backend maneja los favoritos como ids de vacantes del estudiante
// autenticado (tabla FAVORITOS_VACANTES); el expediente se resuelve del JWT
@Injectable({ providedIn: 'root' })
export class FavoritoService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/favoritos';

  getMisFavoritos(): Observable<number[]> {
    return this.http.get<number[]>(`${this.apiUrl}/me`);
  }

  toggle(vacanteId: number): Observable<number[]> {
    return this.http.post<number[]>(`${this.apiUrl}/toggle`, null, {
      params: { vacanteId }
    });
  }
}
