import { TestBed } from '@angular/core/testing';
import { CalificacionModalComponent } from './calificacion-modal';

describe('CalificacionModalComponent', () => {
  function crearComponente(rol: string) {
    const fixture = TestBed.createComponent(CalificacionModalComponent);
    fixture.componentRef.setInput('proceso', 'Prácticas');
    fixture.componentRef.setInput('estudiante', 'Ana Pérez');
    fixture.componentRef.setInput('parcial', 2);
    fixture.componentRef.setInput('rol', rol);
    fixture.componentRef.setInput('notaTutor', 8);
    fixture.componentRef.setInput('notaCoordinacion', 9);
    fixture.detectChanges();
    return fixture;
  }

  it('muestra únicamente el campo correspondiente al rol', () => {
    const fixtureTutor = crearComponente('TUTOR');
    expect(fixtureTutor.nativeElement.querySelector('#nota-tutor')).not.toBeNull();
    expect(fixtureTutor.nativeElement.querySelector('#nota-coordinacion')).toBeNull();

    fixtureTutor.destroy();
    const fixtureCoordinacion = crearComponente('COORDINADOR');
    expect(fixtureCoordinacion.nativeElement.querySelector('#nota-tutor')).toBeNull();
    expect(fixtureCoordinacion.nativeElement.querySelector('#nota-coordinacion')).not.toBeNull();
  });

  it('emite cierre al presionar Escape', () => {
    const fixture = crearComponente('TUTOR');
    let cerrado = false;
    fixture.componentInstance.cerrar.subscribe(() => cerrado = true);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));

    expect(cerrado).toBe(true);
  });
});
