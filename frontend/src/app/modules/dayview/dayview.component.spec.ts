import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DayviewComponent } from './dayview.component';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

describe('DayviewComponent', () => {
  let component: DayviewComponent;
  let fixture: ComponentFixture<DayviewComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DayviewComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    })
    .overrideComponent(DayviewComponent, {set: {template: ''}})
    .compileComponents();

    fixture = TestBed.createComponent(DayviewComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
