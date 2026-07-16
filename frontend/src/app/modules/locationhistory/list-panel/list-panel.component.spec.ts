import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ListPanelComponent } from './list-panel.component';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MessageService } from 'primeng/api';

describe('DayPanelComponent', () => {
  let component: ListPanelComponent;
  let fixture: ComponentFixture<ListPanelComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ListPanelComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), MessageService]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ListPanelComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
