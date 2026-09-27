import {TestBed} from '@angular/core/testing';
import {Subject} from 'rxjs';
import {CurrentUser, CurrentUserService as CurrentUserApi} from '../../../../generated/backend-api/thereabout';
import {canImpersonateLocally, CurrentUserService} from './current-user.service';

describe('CurrentUserService', () => {
  afterEach(() => vi.unstubAllGlobals());
  it('allows simulation only for development builds on loopback', () => {
    for (const host of ['localhost', '127.0.0.1', '[::1]']) {
      expect(canImpersonateLocally(true, host)).toBe(true);
      expect(canImpersonateLocally(false, host)).toBe(false);
    }
    for (const host of ['thereabout.example.com', 'localhost.example.com', '192.168.1.10']) {
      expect(canImpersonateLocally(true, host)).toBe(false);
    }
  });
  it('rejects simulation outside localhost even when invoked directly', () => {
    vi.stubGlobal('location', {hostname: 'thereabout.example.com'});
    TestBed.configureTestingModule({providers: [{provide: CurrentUserApi, useValue: {getCurrentUser: vi.fn()}}]});
    const service = TestBed.inject(CurrentUserService);
    service.impersonate({id: 2, shortName: 'Heidi', isUser: true});
    expect(service.impersonatedUser()).toBeNull();
  });
  it('simulates eligible users across refreshes and restores verified identity when stopped', () => {
    vi.stubGlobal('location', {hostname: 'localhost'});
    const response = new Subject<CurrentUser>();
    const api = {getCurrentUser: vi.fn(() => response)};
    TestBed.configureTestingModule({providers: [{provide: CurrentUserApi, useValue: api}]});
    const service = TestBed.inject(CurrentUserService);
    service.impersonate({id: 3, shortName: 'Group', isUser: true, isGroup: true});
    service.impersonate({id: 4, shortName: 'Contact', isUser: false});
    expect(service.impersonatedUser()).toBeNull();
    service.impersonate({id: 2, shortName: 'Heidi', isUser: true});
    service.load(); response.next({status: 'unlinked'});
    expect(service.state()).toMatchObject({status: 'resolved', identityId: 2, displayName: 'Heidi'});
    service.stopImpersonation();
    expect(service.displayName()).toBeUndefined();
    response.next({status: 'resolved', identityId: 1, displayName: 'Theo'});
    expect(service.displayName()).toBe('Theo');
    expect(service.impersonatedUser()).toBeNull();
  });
  it('clears hidden sessions and verifies again on return or back-forward restoration', () => {
    const api = {getCurrentUser: vi.fn(() => new Subject<CurrentUser>())};
    TestBed.configureTestingModule({providers: [{provide: CurrentUserApi, useValue: api}]});
    const service = TestBed.inject(CurrentUserService);
    service.start();
    service.verifiedState.set({status: 'resolved', identityId: 1, displayName: 'Theo'});
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
