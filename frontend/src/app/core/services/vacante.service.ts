import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Empresa } from './empresa.service';

export interface VacantePractica {
  id?: number;
  nombre: string;
  empresa: Empresa;
  cupos: number;
  horas: number;
  descripcion: string;
  carrera: string;
  modalidadAcademica: 'Práctica I' | 'Práctica II';
  area: string;
  ciudad: string;
  tipoEmpresa: 'Pública' | 'Privada';
  modalidadTrabajo: 'Presencial' | 'Híbrida' | 'Virtual';
  fechaLimite?: string;
  altaDemanda: boolean;
  requisitos?: string;
  perfilRequerido?: string;
  periodoAcademico: string;
  // Fase 42: una vacante pausada se oculta a estudiantes y no admite asignaciones
  activa?: boolean;
}

@Injectable({ providedIn: 'root' })
export class VacanteService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/vacantes';

  getAll(periodoAcademico?: string): Observable<VacantePractica[]> {
    const url = periodoAcademico
      ? `${this.apiUrl}?periodoAcademico=${encodeURIComponent(periodoAcademico)}`
      : this.apiUrl;
    return this.http.get<VacantePractica[]>(url);
  }

  create(vacante: VacantePractica): Observable<VacantePractica> {
    return this.http.post<VacantePractica>(this.apiUrl, vacante);
  }

  update(id: number, vacante: VacantePractica): Observable<VacantePractica> {
    return this.http.put<VacantePractica>(`${this.apiUrl}/${id}`, vacante);
  }

  pausar(id: number): Observable<VacantePractica> {
    return this.http.post<VacantePractica>(`${this.apiUrl}/${id}/pausar`, {});
  }

  reactivar(id: number): Observable<VacantePractica> {
    return this.http.post<VacantePractica>(`${this.apiUrl}/${id}/reactivar`, {});
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
