import { TestBed } from '@angular/core/testing';
import { NEVER } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ImportacionService } from '../../core/services/importacion.service';
import { ToastService } from '../../core/services/toast.service';
import { construirVistaPreviaCsv, ImportacionesComponent } from './importaciones';

describe('importaciones institucionales', () => {
  it('construye una vista previa respetando comas y comillas del CSV', () => {
    const vista = construirVistaPreviaCsv(
      '\uFEFFexternal_id,nombre,carrera\r\n001,"Ana, María",Software\r\n002,"Luis ""Lucho""",Derecho\r\n'
    );

    expect(vista.encabezados).toEqual(['external_id', 'nombre', 'carrera']);
    expect(vista.totalFilas).toBe(2);
    expect(vista.filas).toEqual([
      ['001', 'Ana, María', 'Software'],
      ['002', 'Luis "Lucho"', 'Derecho']
    ]);
  });

  it('limita la muestra sin perder el total de filas detectadas', () => {
    const vista = construirVistaPreviaCsv('id,nombre\n1,Ana\n2,Luis\n3,Sofía\n', 2);

    expect(vista.filas).toHaveLength(2);
    expect(vista.totalFilas).toBe(3);
  });

  it('impide iniciar una segunda importación mientras la primera sigue en proceso', () => {
    const importarEstudiantes = vi.fn(() => NEVER);
    TestBed.configureTestingModule({
      providers: [
        { provide: ImportacionService, useValue: { importarEstudiantes } },
        { provide: ToastService, useValue: { warning: vi.fn(), success: vi.fn(), error: vi.fn() } }
      ]
    });
    const componente = TestBed.runInInjectionContext(() => new ImportacionesComponent());
    componente.archivo.set(new File(['id,nombre\n1,Ana'], 'estudiantes.csv', { type: 'text/csv' }));
    componente.vistaPrevia.set({ encabezados: ['id', 'nombre'], filas: [['1', 'Ana']], totalFilas: 1 });
    componente.confirmacion.set(true);

    componente.importar();
    componente.importar();

    expect(importarEstudiantes).toHaveBeenCalledTimes(1);
    expect(componente.subiendo()).toBe(true);
  });
});
