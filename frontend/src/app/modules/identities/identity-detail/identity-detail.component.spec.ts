import {provideZonelessChangeDetection,signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {ActivatedRoute, convertToParamMap, provideRouter} from '@angular/router';
import {of} from 'rxjs';
import {MessageService} from 'primeng/api';
import {Identity, IdentityService} from '../../../../../generated/backend-api/thereabout';
import {CurrentUserService} from '../../../shared/current-user/current-user.service';
import {IdentityDetailComponent} from './identity-detail.component';

describe('IdentityDetailComponent impersonation', () => {
  async function setup(identity: Identity, allowed = true) {
    const currentUser = {canManageUsers: signal(allowed), impersonationAllowed: signal(allowed), impersonate: vi.fn()};
    await TestBed.configureTestingModule({imports: [IdentityDetailComponent], providers: [
      provideZonelessChangeDetection(), provideRouter([]), MessageService,
      {provide: ActivatedRoute, useValue: {snapshot: {paramMap: convertToParamMap({id: String(identity.id)})}}},
      {provide: IdentityService, useValue: {getIdentities: () => of([identity])}},
      {provide: CurrentUserService, useValue: currentUser}
    ]}).compileComponents();
    const fixture = TestBed.createComponent(IdentityDetailComponent);
    await fixture.whenStable();
    return {fixture, currentUser, button: () => (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('[aria-label="Impersonate"]')};
  }

  it.each(['USER', 'ADMIN'] as const)('lets an admin impersonate a person with role %s', async role => {
    const identity: Identity = {id: 2, shortName: 'Example', role, isGroup: false};
    const {fixture, currentUser, button} = await setup(identity);
    expect(button()).not.toBeNull();
    button()!.click();
    expect(currentUser.impersonate).toHaveBeenCalledWith(identity);
    currentUser.impersonationAllowed.set(false);
    await fixture.whenStable();
    expect(button()).toBeNull();
  });

  it.each([
    {id: 2, shortName: 'Contact'},
    {id: 2, shortName: 'Group', role: 'USER' as const, isGroup: true}
  ])('hides impersonation for an ineligible identity: $shortName', async identity => {
    const {button} = await setup(identity);
    expect(button()).toBeNull();
  });

  it('hides impersonation when the actor is not an admin', async () => {
    const {button} = await setup({id: 2, shortName: 'Example', role: 'USER'}, false);
    expect(button()).toBeNull();
  });
});
