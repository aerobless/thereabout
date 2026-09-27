import {TestBed} from '@angular/core/testing';
import {Subject} from 'rxjs';
import {CurrentUser, CurrentUserService as CurrentUserApi} from '../../../../generated/backend-api/thereabout';
import {CurrentUserService} from './current-user.service';

describe('CurrentUserService', () => {
  it('clears hidden sessions and verifies again on return or back-forward restoration', () => {
    const api = {getCurrentUser: vi.fn(() => new Subject<CurrentUser>())};
    TestBed.configureTestingModule({providers: [{provide: CurrentUserApi, useValue: api}]});
    const service = TestBed.inject(CurrentUserService);
    service.start();
    service.state.set({status: 'resolved', identityId: 1, displayName: 'Theo'});
    const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden');
    document.dispatchEvent(new Event('visibilitychange'));
    expect(service.displayName()).toBeUndefined();
    visibility.mockReturnValue('visible');
    document.dispatchEvent(new Event('visibilitychange'));
    expect(api.getCurrentUser).toHaveBeenCalledTimes(2);
    window.dispatchEvent(new PageTransitionEvent('pageshow', {persisted: true}));
    expect(api.getCurrentUser).toHaveBeenCalledTimes(3);
    visibility.mockRestore();
  });
  it('clears prior identity on every load, ignores stale requests and distinguishes failures', () => {
    const first = new Subject<CurrentUser>(), second = new Subject<CurrentUser>();
    const api = {getCurrentUser: vi.fn().mockReturnValueOnce(first).mockReturnValueOnce(second)};
    TestBed.configureTestingModule({providers: [{provide: CurrentUserApi, useValue: api}]});
    const service = TestBed.inject(CurrentUserService);
    expect(service.state().status).toBe('loading');
    service.load(); first.next({status: 'resolved', identityId: 1, displayName: 'Theo'});
    expect(service.displayName()).toBe('Theo');
    service.load(); expect(service.displayName()).toBeUndefined();
    first.next({status: 'resolved', identityId: 1, displayName: 'Theo'});
    expect(service.displayName()).toBeUndefined();
    second.next({status: 'unlinked'}); expect(service.state().status).toBe('unlinked');
    second.error(new Error('unavailable'));
    expect(service.state().status).toBe('verification_unavailable');
    expect(service.displayName()).toBeUndefined();
  });
});
