import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DayPanelComponent } from './day-panel.component';

describe('DayPanelComponent', () => {
  let component: DayPanelComponent;
  let fixture: ComponentFixture<DayPanelComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DayPanelComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(DayPanelComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
