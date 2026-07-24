import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { EventoLineaTiempo } from '../../../core/services/linea-tiempo.service';
import { LineaTiempoExpedienteComponent, ordenarEventos } from './linea-tiempo-expediente';

const evento = (clave: string, fecha: string): EventoLineaTiempo => ({
  clave,
  tipo: 'registro',
  titulo: clave,
  descripcion: `Descripción ${clave}`,
  fecha
});

describe('LineaTiempoExpedienteComponent', () => {
  it('ordena los eventos ascendentemente y conserva el orden de fechas iguales', () => {
    expect(ordenarEventos([
      evento('dos', '2026-02-02T10:00:00Z'),
      evento('uno', '2026-01-01T10:00:00Z'),
      evento('tres', '2026-02-02T10:00:00Z')
    ]).map(item => item.clave)).toEqual(['uno', 'dos', 'tres']);
  });

  it('expone un diálogo accesible con el estado y los eventos del expediente', () => {
    const fixture = TestBed.createComponent(LineaTiempoExpedienteComponent);
    fixture.componentRef.setInput('lineaTiempo', {
      proceso: 'PRACTICAS',
      expedienteId: 8,
      periodoAcademico: '2026-2',
      estado: 'reprobado',
      motivoFinalizacion: 'Incumplimiento académico documentado',
      eventos: [evento('inicio', '2026-01-01T10:00:00Z')]
    });
    fixture.detectChanges();

    const dialog = fixture.nativeElement.querySelector('[role="dialog"]') as HTMLElement;
    expect(dialog).not.toBeNull();
    expect(dialog.getAttribute('aria-labelledby')).toBe('linea-tiempo-titulo');
    expect(fixture.nativeElement.textContent).toContain('Reprobado');
    expect(fixture.nativeElement.textContent).toContain('Incumplimiento académico documentado');
    expect(fixture.nativeElement.textContent).toContain('inicio');
  });
});
