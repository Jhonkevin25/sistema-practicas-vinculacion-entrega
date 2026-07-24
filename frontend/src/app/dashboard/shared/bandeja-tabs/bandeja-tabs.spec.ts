import { TestBed } from '@angular/core/testing';
import { BandejaTabsComponent } from './bandeja-tabs';

describe('BandejaTabsComponent', () => {
  it('identifica la bandeja activa y emite el cambio seleccionado', () => {
    const fixture = TestBed.createComponent(BandejaTabsComponent);
    fixture.componentRef.setInput('tabs', [
      { id: 'lista', label: 'Asignaciones' },
      { id: 'seguimiento', label: 'Seguimiento' }
    ]);
    fixture.componentRef.setInput('activeTab', 'lista');

    let tabSeleccionada = '';
    fixture.componentInstance.tabChange.subscribe(tab => tabSeleccionada = tab);
    fixture.detectChanges();

    const botones = fixture.nativeElement.querySelectorAll('[role="tab"]') as NodeListOf<HTMLButtonElement>;
    expect(botones.length).toBe(2);
    expect(botones[0].getAttribute('aria-selected')).toBe('true');
    expect(botones[1].getAttribute('aria-selected')).toBe('false');

    botones[1].click();
    expect(tabSeleccionada).toBe('seguimiento');
  });
});
