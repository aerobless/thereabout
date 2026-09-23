import {Component, Input} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {ChartModule} from 'primeng/chart';
import {vi} from 'vitest';
import {EnergyCardComponent} from './energy-card.component';

@Component({selector: 'p-chart', template: ''})
class ChartStub {
  @Input() type: unknown;
  @Input() data: unknown;
  @Input() options: unknown;
  @Input() ariaLabel: unknown;
}

async function setup() {
  await TestBed.configureTestingModule({imports: [EnergyCardComponent]})
    .overrideComponent(EnergyCardComponent, {remove: {imports: [ChartModule]}, add: {imports: [ChartStub]}}).compileComponents();
  const fixture = TestBed.createComponent(EnergyCardComponent);
  fixture.componentRef.setInput('date', '2026-09-23');
  fixture.componentRef.setInput('activeRecords', [{date: '2026-09-23', qty: 500}]);
  fixture.componentRef.setInput('basalRecords', [{date: '2026-09-23', qty: 1500}]);
  fixture.detectChanges();
  return fixture;
}

describe('Energy card', () => {
  it('opens from its summary, switches ranges, and preserves the open modal on new records', async () => {
    const fixture = await setup();
    const component = fixture.componentInstance;
    const card: HTMLButtonElement = fixture.nativeElement.querySelector('.energy-card');
    expect(card.textContent).toContain('500 kcal');
    expect(card.textContent).toContain('Basal');
    card.click(); fixture.detectChanges();
    expect(card.getAttribute('aria-expanded')).toBe('true');
    expect(component.chartData.labels).toHaveLength(30);
    expect(fixture.nativeElement.textContent).toContain('Daily values');
    const ranges = fixture.nativeElement.querySelectorAll('.ranges button');
    ranges[0].click(); fixture.detectChanges();
    expect(component.chartData.labels).toHaveLength(7);
    expect(ranges[0].getAttribute('aria-pressed')).toBe('true');
    expect(component.chartData.datasets[0].data).toEqual([null, null, null, null, null, null, 1500]);
    fixture.componentRef.setInput('activeRecords', [{date: '2026-09-23', qty: 600}]); fixture.detectChanges();
    expect(component.selected.total).toBe(2100);
    expect(component.visible).toBe(true);
    expect(component.days).toBe(7);
    fixture.componentRef.setInput('date', '2026-09-24'); fixture.detectChanges();
    expect(component.visible).toBe(false);
    expect(component.selected.active).toBeNull();
    expect(component.selected.basal).toBeNull();
  });

  it('shows loading, error, retry and empty states independently', async () => {
    const fixture = await setup();
    const component = fixture.componentInstance;
    fixture.componentRef.setInput('loading', true); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Loading energy');
    fixture.nativeElement.querySelector('.energy-card').click(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Loading energy history');
    fixture.componentRef.setInput('loading', false);
    fixture.componentRef.setInput('error', true); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Unable to load energy history');
    const retry = vi.spyOn(component.retry, 'emit');
    const buttons = [...fixture.nativeElement.querySelectorAll('button')] as HTMLButtonElement[];
    buttons.find(button => button.textContent?.trim() === 'Retry')!.click();
    expect(retry).toHaveBeenCalledOnce();
    fixture.componentRef.setInput('error', false);
    fixture.componentRef.setInput('activeRecords', []);
    fixture.componentRef.setInput('basalRecords', []); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No energy data in this 30-day range');
    expect(component.hasData).toBe(false);
  });

  it('labels today and incomplete data and explains unsupported units', async () => {
    const fixture = await setup();
    const now = new Date();
    const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
    fixture.componentRef.setInput('date', today);
    fixture.componentRef.setInput('activeRecords', [{date: today, qty: 0}]);
    fixture.componentRef.setInput('basalRecords', [{date: today, qty: 100, units: 'J'}]); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('So far today');
    expect(fixture.nativeElement.textContent).toContain('Incomplete day');
    fixture.nativeElement.querySelector('.energy-card').click(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Records with unsupported units (J) are excluded');
    expect(fixture.componentInstance.selected.total).toBeNull();
    expect(fixture.componentInstance.selected.active).toBe(0);
  });
});
