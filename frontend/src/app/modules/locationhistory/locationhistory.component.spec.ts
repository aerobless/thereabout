import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LocationhistoryComponent } from './locationhistory.component';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';

describe('LocationhistoryComponent', () => {
  let component: LocationhistoryComponent;
  let fixture: ComponentFixture<LocationhistoryComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LocationhistoryComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), MessageService]
    })
    .overrideComponent(LocationhistoryComponent, {set: {template: ''}})
    .compileComponents();
    
    fixture = TestBed.createComponent(LocationhistoryComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
