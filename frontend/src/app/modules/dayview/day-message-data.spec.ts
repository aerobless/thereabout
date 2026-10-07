import {Component, computed, inject, provideZonelessChangeDetection, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {Subject} from 'rxjs';
import {DayMessageData} from './day-message-data';
import {DayViewData} from './day-view-data';
import {CurrentUserService} from '../../shared/current-user/current-user.service';
import {Message, MessageService} from '../../../../generated/backend-api/thereabout';
@Component({template:'{{data.messages().length}} {{data.messagesDialogVisible()}}',providers:[DayMessageData]})
class MessagesHost { readonly data=inject(DayMessageData); }
describe('Message impersonation privacy',()=>{
  it('closes the dialog, clears messages and rejects delayed responses without another interaction',async()=>{
    const state=signal({status:'resolved',identityId:1});
    const response=new Subject<Message[]>();
    TestBed.configureTestingModule({providers:[provideZonelessChangeDetection(),
      {provide:CurrentUserService,useValue:{state,viewKeys:computed(()=>[String(state().identityId)])}},
      {provide:DayViewData,useValue:{selectedDate:signal(new Date(2026,9,7))}},
      {provide:MessageService,useValue:{getMessages:()=>response}}]});
    const fixture=TestBed.createComponent(MessagesHost);fixture.detectChanges();await fixture.whenStable();
    const data=fixture.componentInstance.data;
    data.loadMessages();
    data.messages.set([{id:1,timestamp:'2026-10-07T12:00:00',body:'Private',type:'text',source:'Telegram',sender:{name:'Theo'},receiver:{name:'Private contact'}}]);
    data.messagesDialogVisible.set(true);
    await fixture.whenStable();expect(fixture.nativeElement.textContent).toContain('1 true');
    state.set({status:'resolved',identityId:2});
    response.next([{id:2,timestamp:'2026-10-07T12:00:00',body:'Stale',type:'text',source:'Telegram',sender:{name:'Theo'},receiver:{name:'Private contact'}}]);
    await fixture.whenStable();expect(fixture.nativeElement.textContent).toContain('0 false');
    response.next([{id:3,timestamp:'2026-10-07T12:00:00',body:'Later stale',type:'text',source:'Telegram',sender:{name:'Theo'},receiver:{name:'Private contact'}}]);
    await fixture.whenStable();expect(fixture.nativeElement.textContent).toContain('0 false');
  });
});
