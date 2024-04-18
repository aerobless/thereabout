import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LocationhistoryComponent } from './locationhistory.component';

describe('LocationhistoryComponent', () => {
  let component: LocationhistoryComponent;
  let fixture: ComponentFixture<LocationhistoryComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LocationhistoryComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(LocationhistoryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
