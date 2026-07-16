import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DayPanelComponent } from './day-panel.component';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MessageService } from 'primeng/api';

describe('DayPanelComponent', () => {
  let component: DayPanelComponent;
  let fixture: ComponentFixture<DayPanelComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DayPanelComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), MessageService]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(DayPanelComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render location rows', () => {
    component.dayViewDataFull = [{
      id: 1,
      timestamp: '2026-07-16T08:00:00Z',
      latitude: 47.37818,
      longitude: 8.54019,
      altitude: 408,
      horizontalAccuracy: 6
    }];

    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('.p-datatable-tbody > tr');
    expect(rows).toHaveLength(1);
    expect(rows[0].textContent).toContain('47.37818');
    expect(rows[0].textContent).toContain('8.54019');
  });
});
