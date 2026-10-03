import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MessageService } from 'primeng/api';
import { OpenAiSettingsComponent } from './openai-settings.component';

describe('OpenAI configuration', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), MessageService] }));
  it('shows only configured status and clears a replacement key after saving', async () => {
    const fixture = TestBed.createComponent(OpenAiSettingsComponent); const http = TestBed.inject(HttpTestingController);
    http.expectOne('http://localhost/backend/api/v1/config/openai').flush({ configured: true, model: 'gpt-6-luna' });
    fixture.detectChanges(); await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('input').type).toBe('password');
    expect(fixture.componentInstance.key()).toBe('');
    fixture.componentInstance.key.set('synthetic-key');
    const pending = fixture.componentInstance.save();
    const save = http.expectOne('http://localhost/backend/api/v1/config/openai');
    expect(save.request.method).toBe('PUT'); expect(save.request.body.apiKey).toBe('synthetic-key');
    save.flush({ configured: true, model: 'gpt-6-luna' }); await pending; await fixture.whenStable();
    expect(fixture.componentInstance.key()).toBe(''); http.verify();
  });
});
