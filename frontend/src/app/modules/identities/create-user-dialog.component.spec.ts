import {TestBed} from '@angular/core/testing';
import {Subject, of, throwError} from 'rxjs';
import {MessageService} from 'primeng/api';
import {Identity, UsersService} from '../../../../generated/backend-api/thereabout';
import {CurrentUserService} from '../../shared/users/current-user.service';
import {CreateUserDialogComponent} from './create-user-dialog.component';

describe('CreateUserDialogComponent', () => {
  const person:Identity={id:2,shortName:'Heidi',isGroup:false,isUser:false};
  function setup(api:any) {
    const refresh=vi.fn(),toast=vi.fn();
    TestBed.configureTestingModule({imports:[CreateUserDialogComponent],providers:[
      {provide:UsersService,useValue:api}, {provide:MessageService,useValue:{add:toast}},
      {provide:CurrentUserService,useValue:{refresh}}
    ]});
    const fixture=TestBed.createComponent(CreateUserDialogComponent);
    fixture.componentRef.setInput('identity',person); fixture.detectChanges();
    return {fixture,component:fixture.componentInstance,refresh,toast};
  }
  it('normalizes email and prevents duplicate submissions or closing while saving', () => {
    const pending=new Subject<Identity>(); const api={createUser:vi.fn(()=>pending)};
    const {component,fixture,refresh,toast}=setup(api);
    const created=vi.fn(),dismissed=vi.fn();component.created.subscribe(created);component.dismissed.subscribe(dismissed);
    component.email='  Heidi@Example.test  ';component.save();component.save();component.close();
    expect(api.createUser).toHaveBeenCalledExactlyOnceWith(2,{email:'heidi@example.test'});
    expect(dismissed).not.toHaveBeenCalled();
    pending.next({...person,isUser:true});
    expect(created).toHaveBeenCalledWith({...person,isUser:true});expect(refresh).toHaveBeenCalledOnce();expect(toast).toHaveBeenCalledOnce();
    fixture.destroy();
  });
  it('keeps the modal and input after a conflicting email', () => {
    const api={createUser:vi.fn(()=>throwError(()=>({status:409})))};
    const {component,fixture}=setup(api);const created=vi.fn();component.created.subscribe(created);
    component.email='heidi@example.test';component.save();fixture.detectChanges();
    expect(component.error()).toContain('already assigned');expect(component.email).toBe('heidi@example.test');
    expect(component.saving()).toBe(false);expect(created).not.toHaveBeenCalled();
    fixture.destroy();
  });
  it('has a required email field and does not offer submission for invalid input', async () => {
    const {fixture}=setup({createUser:vi.fn()});await fixture.whenStable();fixture.detectChanges();
    const input=document.querySelector<HTMLInputElement>('#cloudflare-email');
    expect(input?.type).toBe('email');expect(input?.required).toBe(true);
    const submit=document.querySelector<HTMLButtonElement>('button[type=submit]');expect(submit?.disabled).toBe(true);
    fixture.destroy();
  });
});
