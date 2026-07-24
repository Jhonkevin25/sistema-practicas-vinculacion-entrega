import { Component, Input, Output, EventEmitter, forwardRef, signal, OnInit, OnDestroy, HostListener, ElementRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ControlValueAccessor, NG_VALUE_ACCESSOR, FormsModule } from '@angular/forms';
import { Subject, Observable, Subscription, of } from 'rxjs';
import { debounceTime, switchMap, catchError, tap } from 'rxjs/operators';

@Component({
  selector: 'app-autocomplete',
  standalone: true,
  imports: [CommonModule, FormsModule],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => AutocompleteComponent),
      multi: true
    }
  ],
  template: `
    <div class="relative w-full text-xs">
      <input
        type="text"
        [placeholder]="placeholder"
        [value]="displayValue()"
        (input)="onInput($event)"
        (focus)="onFocus()"
        [disabled]="isDisabled"
        class="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg outline-none focus:border-unibe-blue text-slate-800 disabled:opacity-50"
        [class.border-red-500]="hasError"
      />
      
      <!-- Clear button if a value is selected -->
      @if (selectedItem() && !isDisabled) {
        <button type="button" (click)="clearSelection()" class="absolute right-3 top-2.5 text-slate-400 hover:text-slate-600">
          <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
            <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd" />
          </svg>
        </button>
      }

      <!-- Spinner while loading -->
      @if (isLoading()) {
        <div class="absolute right-3 top-2.5 text-unibe-blue">
          <svg class="animate-spin h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
          </svg>
        </div>
      }

      <!-- Dropdown -->
      @if (isOpen() && !isDisabled) {
        <div class="absolute z-50 w-full mt-1 bg-white border border-slate-200 rounded-lg shadow-lg max-h-60 overflow-y-auto">
          @if (results().length === 0 && !isLoading()) {
            <div class="p-3 text-slate-500 text-center italic text-xs">No se encontraron resultados.</div>
          }
          @for (item of results(); track trackByFn(item)) {
            <div 
              class="px-3 py-2 cursor-pointer hover:bg-slate-50 text-slate-700 text-xs border-b border-slate-100 last:border-0"
              (click)="selectItem(item)">
              {{ displayFn(item) }}
            </div>
          }
        </div>
      }
    </div>
  `
})
export class AutocompleteComponent<T> implements ControlValueAccessor, OnInit, OnDestroy {
  @Input() placeholder: string = 'Buscar...';
  @Input() searchFn!: (query: string) => Observable<T[]>;
  @Input() displayFn: (item: T) => string = (item: any) => String(item);
  @Input() trackByFn: (item: T) => any = (item: any) => item.id || item;
  @Input() hasError: boolean = false;
  
  // Custom fetch to resolve the initial ID to an object if needed
  @Input() resolveFn?: (id: any) => Observable<T>;

  @Output() itemSelected = new EventEmitter<T | null>();

  results = signal<T[]>([]);
  isLoading = signal<boolean>(false);
  isOpen = signal<boolean>(false);
  selectedItem = signal<T | null>(null);
  displayValue = signal<string>('');

  isDisabled: boolean = false;

  private searchSubject = new Subject<string>();
  private sub?: Subscription;
  private elRef = inject(ElementRef);

  onChange: any = () => {};
  onTouch: any = () => {};

  ngOnInit() {
    this.sub = this.searchSubject.pipe(
      debounceTime(300),
      tap(() => this.isLoading.set(true)),
      switchMap(query => {
        if (!query.trim()) {
          this.isLoading.set(false);
          return of([]);
        }
        return this.searchFn(query).pipe(
          catchError(() => of([])),
          tap(() => this.isLoading.set(false))
        );
      })
    ).subscribe(res => {
      this.results.set(res);
      this.isOpen.set(true);
    });
  }

  ngOnDestroy() {
    this.sub?.unsubscribe();
  }

  onInput(event: Event) {
    const val = (event.target as HTMLInputElement).value;
    this.displayValue.set(val);
    
    // If user types, we clear the actual selected item model
    if (this.selectedItem()) {
      this.selectedItem.set(null);
      this.onChange(null);
      this.itemSelected.emit(null);
    }

    if (val.trim().length >= 2) {
      this.searchSubject.next(val);
    } else {
      this.isOpen.set(false);
      this.results.set([]);
    }
  }

  onFocus() {
    if (!this.selectedItem() && this.displayValue().trim().length >= 2) {
      this.isOpen.set(true);
    } else if (!this.selectedItem() && this.results().length > 0) {
      this.isOpen.set(true);
    }
  }

  selectItem(item: T) {
    this.selectedItem.set(item);
    this.displayValue.set(this.displayFn(item));
    this.isOpen.set(false);
    
    // Return ID if the item has one, otherwise the object
    const valToEmit = (item as any).id !== undefined ? (item as any).id : item;
    this.onChange(valToEmit);
    this.itemSelected.emit(item);
  }

  clearSelection() {
    this.selectedItem.set(null);
    this.displayValue.set('');
    this.results.set([]);
    this.isOpen.set(false);
    this.onChange(null);
    this.itemSelected.emit(null);
  }

  @HostListener('document:click', ['$event'])
  clickout(event: Event) {
    if (!this.elRef.nativeElement.contains(event.target)) {
      this.isOpen.set(false);
    }
  }

  // ControlValueAccessor methods
  writeValue(obj: any): void {
    if (obj == null) {
      this.selectedItem.set(null);
      this.displayValue.set('');
      return;
    }

    // If obj is an ID and we have a resolve function
    if (typeof obj === 'number' && this.resolveFn) {
      this.resolveFn(obj).subscribe({
        next: item => {
          this.selectedItem.set(item);
          this.displayValue.set(this.displayFn(item));
        },
        error: () => {
          this.displayValue.set(String(obj));
        }
      });
    } else if (typeof obj === 'object') {
      this.selectedItem.set(obj);
      this.displayValue.set(this.displayFn(obj));
    } else {
      // Just set ID string representation
      this.displayValue.set(String(obj));
    }
  }

  registerOnChange(fn: any): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: any): void {
    this.onTouch = fn;
  }

  setDisabledState?(isDisabled: boolean): void {
    this.isDisabled = isDisabled;
  }
}
