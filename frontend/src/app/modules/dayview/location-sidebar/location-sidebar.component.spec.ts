import {TestBed} from '@angular/core/testing';
import {LocationSidebarComponent} from './location-sidebar.component';
import {vi} from 'vitest';

describe('LocationSidebarComponent', () => {
  async function setup() {
    await TestBed.configureTestingModule({imports: [LocationSidebarComponent]}).compileComponents();
    const fixture = TestBed.createComponent(LocationSidebarComponent);
    fixture.componentInstance.date = new Date(2026, 8, 15);
    fixture.detectChanges();
    return fixture;
  }

  it('renders entries with stable local times and missing measurement placeholders', async () => {
    const fixture = await setup();
    fixture.componentInstance.entries = [{id: 1, latitude: 47.37818, longitude: 8.54019, timestamp: new Date(2026, 8, 15, 14, 5).toISOString()}];
    fixture.detectChanges();
    const row = fixture.nativeElement.querySelector('.p-datatable-tbody tr');
    expect(row.textContent).toContain('14:05');
    expect(row.textContent).toContain('47.37818');
    expect(row.textContent).toContain('8.54019');
    expect(row.textContent).not.toContain('undefined');
    expect(fixture.nativeElement.querySelector('button[aria-label="Photos"]')).toBeNull();
  });

  it('emits immutable day navigation and rejects empty dates or navigation during a write', async () => {
    const fixture = await setup();
    const component = fixture.componentInstance;
    const original = component.date.getTime();
    const emit = vi.spyOn(component.dateChange, 'emit');
    component.shiftDate(1);
    expect(emit).toHaveBeenCalledWith(new Date(2026, 8, 16));
    expect(component.date.getTime()).toBe(original);
    component.changeDate(null);
    component.saving = true;
    component.shiftDate(-1);
    expect(emit).toHaveBeenCalledOnce();
  });

  it('disables invalid actions for empty and multiple selections and while busy', async () => {
    const fixture = await setup();
    const component = fixture.componentInstance;
    const disabled = (text: string) => [...fixture.nativeElement.querySelectorAll('button')].find((button: any) => button.textContent.trim() === text) as HTMLButtonElement;
    expect(disabled('Locate').disabled).toBe(true);
    expect(disabled('New').disabled).toBe(false);
    expect(disabled('Edit').disabled).toBe(true);
    expect(disabled('Delete').disabled).toBe(true);
    component.selection = [{id: 1}, {id: 2}] as any;
    fixture.detectChanges();
    expect(disabled('New').disabled).toBe(true);
    expect(disabled('Delete').disabled).toBe(false);
    component.busy = true;
    fixture.detectChanges();
    expect(disabled('Delete').disabled).toBe(true);
  });
});
