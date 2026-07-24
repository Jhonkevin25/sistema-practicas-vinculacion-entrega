import { TestBed } from '@angular/core/testing';
import { DocEstudiante } from '../../../core/services/documento.service';
import { DocumentosRevisionPanelComponent } from './documentos-revision-panel';

describe('DocumentosRevisionPanelComponent', () => {
  it('agrupa documentos por estudiante y comunica la carga de una acción', () => {
    const fixture = TestBed.createComponent(DocumentosRevisionPanelComponent);
    const documentos: DocEstudiante[] = [
      documento(1, 20, 'Bruno Ruiz', 'cedula'),
      documento(2, 10, 'Ana Torres', 'cv'),
      documento(3, 10, 'Ana Torres', 'carta')
    ];
    fixture.componentRef.setInput('documentos', documentos);
    fixture.detectChanges();

    const grupos = fixture.componentInstance.grupos();
    expect(grupos.length).toBe(2);
    expect(grupos[0].nombre).toBe('Ana Torres');
    expect(grupos[0].documentos.length).toBe(2);
    expect(fixture.nativeElement.querySelectorAll('[data-estudiante]').length).toBe(2);
    expect(fixture.nativeElement.textContent).toContain('Revisar 2 documentos');
    expect(fixture.nativeElement.textContent).not.toContain('Hoja de vida');

    const primerGrupo = fixture.nativeElement.querySelector('[data-estudiante] > button') as HTMLButtonElement;
    primerGrupo.click();
    fixture.componentRef.setInput('accionEnCurso', { id: 2, tipo: 'ver' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Hoja de vida');
    expect(fixture.nativeElement.textContent).toContain('Carta de solicitud');
    expect(fixture.nativeElement.textContent).toContain('Ocultar documentos');
    expect(fixture.nativeElement.textContent).toContain('Abriendo…');
  });

  it('mantiene abierto un solo expediente estudiantil a la vez', () => {
    const fixture = TestBed.createComponent(DocumentosRevisionPanelComponent);
    fixture.componentRef.setInput('documentos', [
      documento(1, 10, 'Ana Torres', 'cv'),
      documento(2, 20, 'Bruno Ruiz', 'cedula')
    ]);
    fixture.detectChanges();

    const botones = fixture.nativeElement.querySelectorAll('[data-estudiante] > button') as NodeListOf<HTMLButtonElement>;
    botones[0].click();
    fixture.detectChanges();
    expect(botones[0].getAttribute('aria-expanded')).toBe('true');

    botones[1].click();
    fixture.detectChanges();
    expect(botones[0].getAttribute('aria-expanded')).toBe('false');
    expect(botones[1].getAttribute('aria-expanded')).toBe('true');
  });
});

function documento(
  id: number,
  estudianteId: number,
  nombreCompleto: string,
  tipoDocumento: string
): DocEstudiante {
  const [nombre, ...apellidos] = nombreCompleto.split(' ');
  return {
    id,
    tipoDocumento,
    proceso: 'VINCULACION',
    estado: 'cargado',
    estudiante: {
      id: estudianteId,
      carrera: 'Derecho',
      usuario: {
        nombre,
        apellido: apellidos.join(' '),
        email: `${nombre.toLowerCase()}@est.unibe.edu.ec`
      }
    }
  };
}
