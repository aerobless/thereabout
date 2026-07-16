import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TripsComponent } from './trips.component';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { vi } from 'vitest';

describe('TripsComponent', () => {
  let component: TripsComponent;
  let fixture: ComponentFixture<TripsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TripsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), MessageService]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(TripsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('opens a trip as a date range without passing its id', () => {
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    component.viewTrip({
      id: 61,
      title: 'Demo trip',
      description: 'Three cities',
      start: '2026-07-14T00:00:00.000',
      end: '2026-07-16T00:00:00.000'
    });

    expect(navigate).toHaveBeenCalledWith(['locationhistory'], {
      queryParams: {fromDate: '2026-07-14', toDate: '2026-07-16'}
    });
  });
});
