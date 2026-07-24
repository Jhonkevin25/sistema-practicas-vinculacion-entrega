import { Component, input, output } from '@angular/core';

export interface BandejaTab {
  id: string;
  label: string;
  emphasis?: 'normal' | 'warning';
}

@Component({
  selector: 'app-bandeja-tabs',
  standalone: true,
  templateUrl: './bandeja-tabs.html'
})
export class BandejaTabsComponent {
  tabs = input.required<BandejaTab[]>();
  activeTab = input.required<string>();
  tabChange = output<string>();
}
