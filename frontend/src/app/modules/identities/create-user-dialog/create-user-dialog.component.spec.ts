import {TestBed} from '@angular/core/testing';
import {Subject} from 'rxjs';
import {MessageService} from 'primeng/api';
import {Identity, IdentityService} from '../../../../../generated/backend-api/thereabout';
import {CurrentUserService} from '../../../shared/current-user/current-user.service';
import {CreateUserDialogComponent, normalizeCloudflareEmail, validCloudflareEmail} from './create-user-dialog.component';

describe('CreateUserDialogComponent', () => {
  const identity: Identity = {id: 42, shortName: 'Heidi', isGroup: false, isUser: false};
  async function setup() {
    const response = new Subject<Identity>();
    const api = {createIdentityUser: vi.fn(() => response)};
    const messages = {add: vi.fn()}, currentUser = {load: vi.fn()};
    await TestBed.configureTestingModule({imports: [CreateUserDialogComponent], providers: [
      {provide: IdentityService, useValue: api}, {provide: MessageService, useValue: messages},
      {provide: CurrentUserService, useValue: currentUser}
    ]}).compileComponents();
    const fixture = TestBed.createComponent(CreateUserDialogComponent);
    fixture.componentRef.setInput('identity', identity); fixture.detectChanges();
    return {fixture, page: fixture.componentInstance, response, api, messages, currentUser};
  }
  it('validates and normalizes the same email shape as the backend', () => {
    expect(normalizeCloudflareEmail(' \u00a0Heidi@Example.test\uFEFF ')).toBe('heidi@example.test');
    for (const email of ['', 'a', 'a@b', 'a..b@example.test', 'a@-bad.test', 'x'.repeat(65) + '@example.test', 'a@' + 'x'.repeat(64) + '.test']) {
      expect(validCloudflareEmail(email)).toBe(false);
    }
    expect(validCloudflareEmail('heidi+test@example.test')).toBe(true);
  });
  it('shows the required email and field errors without closing', async () => {
    const {fixture, page, api} = await setup();
    page.create(); fixture.detectChanges();
    expect(api.createIdentityUser).not.toHaveBeenCalled();
    expect(document.querySelector('#cloudflare-email')?.hasAttribute('required')).toBe(true);
    expect(document.querySelector('[role="alert"]')?.textContent).toContain('valid Cloudflare email');
    fixture.destroy();
  });
  it('prevents double clicks, refreshes the current user and emits success', async () => {
    const {fixture, page, api, response, messages, currentUser} = await setup();
    const closed = vi.fn(), created = vi.fn(); page.closed.subscribe(closed); page.created.subscribe(created);
    page.email = ' Heidi@Example.test '; page.create(); page.create(); page.close();
    expect(api.createIdentityUser).toHaveBeenCalledExactlyOnceWith(42, {email: 'heidi@example.test'});
    expect(page.busy()).toBe(true); expect(closed).not.toHaveBeenCalled();
    const updated = {...identity, isUser: true}; response.next(updated);
    expect(created).toHaveBeenCalledWith(updated); expect(closed).toHaveBeenCalledOnce();
    expect(messages.add).toHaveBeenCalledOnce(); expect(currentUser.load).toHaveBeenCalledOnce();
    fixture.destroy();
  });
  it('retains the email and dialog on a conflict and lets Cancel close it', async () => {
    const {fixture, page, response} = await setup();
    const closed = vi.fn(); page.closed.subscribe(closed);
    page.email = 'heidi@example.test'; page.create(); response.error({status: 409}); fixture.detectChanges();
    expect(page.email).toBe('heidi@example.test'); expect(page.busy()).toBe(false);
    expect(document.querySelector('[role="alert"]')?.textContent).toContain('already assigned');
    expect(closed).not.toHaveBeenCalled(); page.close(); expect(closed).toHaveBeenCalledOnce();
    fixture.destroy();
  });
});
