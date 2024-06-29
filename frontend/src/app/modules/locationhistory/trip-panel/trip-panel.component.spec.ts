import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TripPanelComponent } from './trip-panel.component';

describe('TripPanelComponent', () => {
  let component: TripPanelComponent;
  let fixture: ComponentFixture<TripPanelComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TripPanelComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(TripPanelComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
