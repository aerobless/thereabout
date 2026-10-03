import {signal} from '@angular/core';
import {CurrentUserService} from './shared/current-user/current-user.service';
import { TestBed } from '@angular/core/testing';
import { AppComponent } from './app.component';
import { MessageService } from 'primeng/api';
import { provideRouter } from '@angular/router';

describe('AppComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [{provide: CurrentUserService, useValue: {canManageUsers: signal(true), viewKeys: signal(['1']), impersonatedUser: signal(null), stopImpersonation: vi.fn()}}, MessageService, provideRouter([])],
    }).compileComponents();
  });

  it('shows the simulated user globally and the close action ends simulation', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const currentUser = TestBed.inject(CurrentUserService);
    const mock = TestBed.inject(CurrentUserService);
    // The fixture receives a writable signal through its mock provider.
    const target = mock.impersonatedUser;
    (target as import('@angular/core').WritableSignal<{id: number; firstName: string} | null>).set({id: 2, firstName: 'Heidi'});
    fixture.detectChanges();
    const banner = fixture.nativeElement.querySelector('.impersonation-banner');
    expect(banner.textContent).toContain('Heidi');
    banner.querySelector('button').click();
    expect(currentUser.stopImpersonation).toHaveBeenCalledOnce();
  });
  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it(`should have the 'thereabout' title`, () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app.title).toEqual('thereabout');
  });

  it('renders the shell without waiting for map configuration', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('thereabout-app-shell')).not.toBeNull();
    expect(compiled.querySelector('router-outlet')).not.toBeNull();
  });
});
