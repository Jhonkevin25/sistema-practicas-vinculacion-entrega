import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, shareReplay, tap } from 'rxjs';

export interface Carrera {
  id?: number;
  nombre: string;
  activo?: boolean;
}

@Injectable({ providedIn: 'root' })
export class CarreraService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/carreras';

  // Fase 43: el catálogo se descarga una sola vez por sesión y se comparte
  // entre pantallas; toda escritura invalida la caché.
  private cache$: Observable<Carrera[]> | null = null;

  getAll(): Observable<Carrera[]> {
    if (!this.cache$) {
      this.cache$ = this.http.get<Carrera[]>(this.apiUrl).pipe(
        // Un error no debe quedar cacheado: el siguiente uso reintenta
        tap({ error: () => this.invalidar() }),
        shareReplay(1)
      );
    }
    return this.cache$;
  }

  create(carrera: Carrera): Observable<Carrera> {
    return this.http.post<Carrera>(this.apiUrl, carrera).pipe(tap(() => this.invalidar()));
  }

  update(id: number, carrera: Carrera): Observable<Carrera> {
    return this.http.put<Carrera>(`${this.apiUrl}/${id}`, carrera).pipe(tap(() => this.invalidar()));
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`).pipe(tap(() => this.invalidar()));
  }

  // Publica para que AuthService.logout() no arrastre la caché a otra sesión
  invalidar(): void {
    this.cache$ = null;
  }
}
