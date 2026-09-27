import {TestBed} from '@angular/core/testing';
import {Subject, of, throwError} from 'rxjs';
import {CurrentUser, UsersService} from '../../../../generated/backend-api/thereabout';
import {CurrentUserService} from './current-user.service';

describe('CurrentUserService', () => {
  it('clears the previous person and ignores a stale response after a refresh', () => {
    const first = new Subject<CurrentUser>(), second = new Subject<CurrentUser>();
    const api = {getCurrentUser: vi.fn().mockReturnValueOnce(of({status:'AUTHENTICATED',displayName:'Theo'}))
      .mockReturnValueOnce(first).mockReturnValueOnce(second)};
    TestBed.configureTestingModule({providers:[{provide:UsersService,useValue:api}]});
    const service = TestBed.inject(CurrentUserService);
    service.refresh(); expect(service.displayName()).toBe('Theo');
    service.refresh(); expect(service.displayName()).toBe('');
    service.refresh(); second.next({status:'AUTHENTICATED',displayName:'Heidi'});
    first.next({status:'AUTHENTICATED',displayName:'Theo'});
    expect(service.displayName()).toBe('Heidi');
  });
  it('distinguishes unassigned users from transport failures', () => {
    const api = {getCurrentUser: vi.fn().mockReturnValueOnce(of({status:'UNASSIGNED'}))
      .mockReturnValueOnce(throwError(() => new Error('offline')))};
    TestBed.configureTestingModule({providers:[{provide:UsersService,useValue:api}]});
    const service=TestBed.inject(CurrentUserService);
    service.refresh(); expect(service.notice()).toBe('No Thereabout user assigned');
    service.refresh(); expect(service.user()?.status).toBe('UNAVAILABLE'); expect(service.displayName()).toBe('');
  });
});
