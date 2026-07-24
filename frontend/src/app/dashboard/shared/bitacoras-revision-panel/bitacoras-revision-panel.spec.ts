import { TestBed } from '@angular/core/testing';
import { Asistencia } from '../../../core/services/asistencia.service';
import { Bitacora } from '../../../core/services/bitacora.service';
import { BitacorasRevisionPanelComponent, RevisionBitacora } from './bitacoras-revision-panel';

describe('BitacorasRevisionPanelComponent', () => {
  const bitacora = {
    id: 14,
    estudiante: {
      usuario: { nombre: 'Ana', apellido: 'Torres' }
    },
    parcial: 1,
    fecha: '2026-07-18',
    actividad: 'Orientación jurídica comunitaria',
    horas: 4,
    estado: 'pendiente'
  } as Bitacora;

  it('exige una observación antes de emitir el rechazo', async () => {
    await TestBed.configureTestingModule({
      imports: [BitacorasRevisionPanelComponent]
    }).compileComponents();

    const fixture = TestBed.createComponent(BitacorasRevisionPanelComponent);
    fixture.componentRef.setInput('bitacoras', [bitacora]);
    const emitidos: RevisionBitacora[] = [];
    fixture.componentInstance.procesar.subscribe(evento => emitidos.push(evento));
    fixture.detectChanges();

    const botones = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    botones.find(boton => boton.textContent?.trim() === 'Rechazar')?.click();
    fixture.detectChanges();

    const botonesModal = Array.from(
      fixture.nativeElement.querySelectorAll('button')
    ) as HTMLButtonElement[];
    const confirmar = botonesModal
      .find(boton => boton.textContent?.trim() === 'Confirmar rechazo')!;
    expect(confirmar.disabled).toBe(true);

    const textarea = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    textarea.value = 'Detallar los resultados obtenidos.';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(confirmar.disabled).toBe(false);
    confirmar.click();
    expect(emitidos).toEqual([{
      bitacora,
      estado: 'rechazada',
      observacion: 'Detallar los resultados obtenidos.'
    }]);
  });

  it('agrupa las bitácoras por estudiante con su contador de pendientes', async () => {
    await TestBed.configureTestingModule({
      imports: [BitacorasRevisionPanelComponent]
    }).compileComponents();

    const fixture = TestBed.createComponent(BitacorasRevisionPanelComponent);
    fixture.componentRef.setInput('bitacoras', [
      bitacora,
      { ...bitacora, id: 15, fecha: '2026-07-17', estado: 'aprobada' },
      {
        ...bitacora,
        id: 16,
        estudiante: { id: 9, matricula: 'MAT-2025-009', usuario: { nombre: 'Bruno', apellido: 'Paz' } }
      }
    ] as Bitacora[]);
    fixture.detectChanges();

    const grupos = fixture.nativeElement.querySelectorAll('details') as NodeListOf<HTMLDetailsElement>;
    expect(grupos.length).toBe(2);
    const texto = (fixture.nativeElement as HTMLElement).textContent || '';
    expect(texto).toContain('Ana Torres');
    expect(texto).toContain('Bruno Paz');
    expect(texto).toContain('1 pendiente(s)');
  });

  it('avisa cuando una bitácora aprobada no tiene asistencia coincidente', async () => {
    await TestBed.configureTestingModule({
      imports: [BitacorasRevisionPanelComponent]
    }).compileComponents();

    const aprobada = {
      ...bitacora,
      id: 20,
      estado: 'aprobada',
      vinculacion: { id: 5 }
    } as Bitacora;

    const fixture = TestBed.createComponent(BitacorasRevisionPanelComponent);
    fixture.componentRef.setInput('bitacoras', [aprobada]);
    fixture.componentRef.setInput('asistencias', []);
    fixture.componentInstance.filtroEstado.set('aprobada');
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Sin asistencia ese día');

    fixture.componentRef.setInput('asistencias', [
      { id: 1, fecha: '2026-07-18', vinculacion: { id: 5 }, estado: 'Presente' } as Asistencia
    ]);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Sin asistencia ese día');
  });

  it('filtra por estado y por parcial ocultando los grupos sin coincidencias', async () => {
    await TestBed.configureTestingModule({
      imports: [BitacorasRevisionPanelComponent]
    }).compileComponents();

    const fixture = TestBed.createComponent(BitacorasRevisionPanelComponent);
    fixture.componentRef.setInput('bitacoras', [
      bitacora,
      {
        ...bitacora,
        id: 21,
        parcial: 2,
        estado: 'aprobada',
        estudiante: { id: 9, matricula: 'MAT-2025-009', usuario: { nombre: 'Bruno', apellido: 'Paz' } }
      }
    ] as Bitacora[]);
    fixture.detectChanges();

    // Por defecto solo pendientes: Ana visible, Bruno (aprobada) oculto
    let texto = (fixture.nativeElement as HTMLElement).textContent || '';
    expect(texto).toContain('Ana Torres');
    expect(texto).not.toContain('Bruno Paz');

    fixture.componentInstance.filtroEstado.set('aprobada');
    fixture.detectChanges();
    texto = (fixture.nativeElement as HTMLElement).textContent || '';
    expect(texto).toContain('Bruno Paz');
    expect(texto).not.toContain('Ana Torres');

    fixture.componentInstance.filtroEstado.set('TODAS');
    fixture.componentInstance.filtroParcial.set(1);
    fixture.detectChanges();
    texto = (fixture.nativeElement as HTMLElement).textContent || '';
    expect(texto).toContain('Ana Torres');
    expect(texto).not.toContain('Bruno Paz');
  });
});
