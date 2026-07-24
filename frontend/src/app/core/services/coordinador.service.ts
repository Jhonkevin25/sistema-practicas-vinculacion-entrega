import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface AlcanceCoordinadorAdmin {
  tipo: 'PRACTICAS' | 'VINCULACION' | 'AMBOS';
  carreras: string[];
}

// Administración del alcance de coordinadores (solo ADMIN);
// el coordinador consulta el propio vía /alcance/me en AuthService.
@Injectable({ providedIn: 'root' })
export class CoordinadorService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/coordinadores';

  getAlcance(usuarioId: number): Observable<AlcanceCoordinadorAdmin> {
    return this.http.get<AlcanceCoordinadorAdmin>(`${this.apiUrl}/${usuarioId}/alcance`);
  }

  setAlcance(usuarioId: number, alcance: AlcanceCoordinadorAdmin): Observable<AlcanceCoordinadorAdmin> {
    return this.http.put<AlcanceCoordinadorAdmin>(`${this.apiUrl}/${usuarioId}/alcance`, alcance);
  }
}
