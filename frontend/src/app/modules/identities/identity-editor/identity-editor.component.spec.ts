import {TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {MessageService} from 'primeng/api';
import {IdentityService, IdentityInApplicationService} from '../../../../../generated/backend-api/thereabout';
import {IdentityEditorComponent} from './identity-editor.component';

describe('IdentityEditorComponent', () => {
  it('preserves user status and Cloudflare link while editing, and retains a failed draft', async () => {
    const cloudflare = {id: 10, application: 'Cloudflare', identifier: 'heidi@example.test'};
    const telegram = {id: 11, application: 'Telegram', identifier: 'heidi'};
    const api = {updateIdentity: vi.fn(() => throwError(() => ({status: 500})))};
    const links = {unlinkIdentityInApplication: vi.fn(() => of(telegram))};
    await TestBed.configureTestingModule({imports: [IdentityEditorComponent], providers: [
      {provide: IdentityService, useValue: api}, {provide: IdentityInApplicationService, useValue: links}, MessageService
    ]}).compileComponents();
    const fixture = TestBed.createComponent(IdentityEditorComponent);
    fixture.componentRef.setInput('identity', {id: 1, shortName: 'Heidi', role: 'USER' as const, identityInApplications: [cloudflare, telegram]});
    fixture.detectChanges(); const page = fixture.componentInstance;
    page.unlinkAppIdentity(cloudflare); expect(links.unlinkIdentityInApplication).not.toHaveBeenCalled();
    page.unlinkAppIdentity(telegram); expect(page.editingIdentity.identityInApplications).toEqual([cloudflare]);
    page.editingIdentity.shortName = 'Heidi updated'; page.saveIdentity();
    expect(api.updateIdentity).toHaveBeenCalledWith(1, expect.objectContaining({role: 'USER' as const, shortName: 'Heidi updated', identityInApplications: [cloudflare]}));
    expect(page.error()).toContain('Unable to save'); expect(page.editingIdentity.shortName).toBe('Heidi updated');
    fixture.destroy();
  });
});
