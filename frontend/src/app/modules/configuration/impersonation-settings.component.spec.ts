import {TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {of} from 'rxjs';
import {IdentityService} from '../../../../generated/backend-api/thereabout';
import {CurrentUserService} from '../../shared/current-user/current-user.service';
import {ImpersonationSettingsComponent} from './impersonation-settings.component';

describe('ImpersonationSettingsComponent', () => {
  async function setup(allowed: boolean) {
    const user = {id: 1, shortName: 'Heidi', isUser: true};
    const current = {impersonationAllowed: allowed, impersonatedUser: signal(null), impersonate: vi.fn()};
    const api = {getIdentities: vi.fn(() => of([user, {id: 2, shortName: 'Group', isUser: true, isGroup: true}, {id: 3, shortName: 'Contact'}]))};
    await TestBed.configureTestingModule({imports: [ImpersonationSettingsComponent], providers: [
      {provide: CurrentUserService, useValue: current}, {provide: IdentityService, useValue: api}
    ]}).compileComponents();
    const fixture = TestBed.createComponent(ImpersonationSettingsComponent); fixture.detectChanges();
    return {fixture, current, api, user};
  }
  it('lists only person users and starts the selected simulation', async () => {
    const {fixture, current, user} = await setup(true);
    expect(fixture.componentInstance.users()).toEqual([user]);
    fixture.componentInstance.selected = user; fixture.componentInstance.start();
    expect(current.impersonate).toHaveBeenCalledWith(user);
  });
  it('hides the card and avoids loading users when unavailable', async () => {
    const {fixture, api} = await setup(false);
    expect(fixture.nativeElement.textContent.trim()).toBe('');
    expect(api.getIdentities).not.toHaveBeenCalled();
  });
});
