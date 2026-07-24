import { TestBed } from '@angular/core/testing';
import { Asistencia } from '../../../core/services/asistencia.service';
import { AsistenciasHistorialPanelComponent } from './asistencias-historial-panel';

describe('AsistenciasHistorialPanelComponent', () => {
  it('muestra el registro de asistencia más reciente', async () => {
    await TestBed.configureTestingModule({ imports: [AsistenciasHistorialPanelComponent] }).compileComponents();
    const fixture = TestBed.createComponent(AsistenciasHistorialPanelComponent);
    fixture.componentRef.setInput('asistencias', [{
      id: 1,
      estudiante: { usuario: { nombre: 'Ana', apellido: 'Torres' } },
      fecha: '2026-07-18',
      horaIngreso: '08:00',
      horaSalida: '12:00',
      estado: 'Presente',
      observaciones: 'Jornada comunitaria'
    } as Asistencia]);
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent || '';
    expect(texto).toContain('Ana Torres');
    expect(texto).toContain('08:00');
    expect(texto).toContain('Presente');
  });
});
