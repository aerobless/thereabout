import {TestBed} from '@angular/core/testing';
import {describe, beforeEach, expect, it, vi} from 'vitest';
import {of, Subject, throwError} from 'rxjs';
import {CalendarService, CalendarOccurrence, GoogleCalendarStatus} from '../../../../generated/backend-api/thereabout';
import {GoogleCalendarSettingsComponent} from './google-calendar-settings.component';
import {CalendarCardComponent} from './calendar-card.component';
import {MessageService} from 'primeng/api';
import {RefreshCoordinator} from '../../shared/refresh/refresh-coordinator';

const status: GoogleCalendarStatus = {account:'me@example.com',state:'READY',error:null,webhookUrl:'https://example.com/backend/api/v1/calendar/google/notifications',webhookHealth:'HEALTHY',secrets:{clientId:true,clientSecret:true,refreshToken:true},calendars:[]};
const sample: CalendarOccurrence = {key:'one',calendarId:1,calendarName:'Personal',color:null,syncing:true,eventId:'event',originalStart:null,recurring:false,title:'Meeting',location:'Zurich',description:null,start:'2026-09-24T07:00:00Z',end:'2026-09-24T08:00:00Z',allDay:false,startDate:null,endDate:null,timeZone:'Europe/Zurich',canDelete:true,guests:[]};
describe('calendar settings and day state', () => {
  let api: any;
  let toast: {add: ReturnType<typeof vi.fn>};
  beforeEach(() => {
    api={getGoogleCalendarStatus:vi.fn(() => of(status)),revealGoogleCalendarSecret:vi.fn(() => of({value:'actual-secret'})),saveGoogleCalendarCredentials:vi.fn(() => of(status)),getCalendarDay:vi.fn(() => of([sample])),deleteCalendarEvent:vi.fn(() => of(undefined))};
    toast={add:vi.fn()};
    TestBed.configureTestingModule({providers:[{provide:CalendarService,useValue:api},{provide:MessageService,useValue:toast},RefreshCoordinator]});
  });
  it('does not fetch secrets until focus and clears unedited reveals on blur', () => {
    const page=TestBed.runInInjectionContext(() => new GoogleCalendarSettingsComponent());
    page.load();
    expect(api.revealGoogleCalendarSecret).not.toHaveBeenCalled();
    page.focus('refreshToken');
    expect(page.values.refreshToken).toBe('actual-secret');
    page.blur('refreshToken');
    expect(page.values.refreshToken).toBeUndefined();
    expect(page.focused).toBeNull();
  });
  it('does not retain a reveal that completes after blur', () => {
    const response=new Subject<{value:string}>(); api.revealGoogleCalendarSecret.mockReturnValue(response);
    const page=TestBed.runInInjectionContext(() => new GoogleCalendarSettingsComponent());
    page.load(); page.focus('refreshToken'); page.blur('refreshToken'); response.next({value:'late'});
    expect(page.values.refreshToken).toBeUndefined();
  });
  it('saves only edited credentials, including explicit clearing', () => {
    const page=TestBed.runInInjectionContext(() => new GoogleCalendarSettingsComponent());
    page.load(); page.change('refreshToken',''); page.saveSecrets();
    expect(api.saveGoogleCalendarCredentials).toHaveBeenCalledWith({refreshToken:''});
    expect(page.values).toEqual({});
  });
  it('preserves settings drafts while refreshing status', () => {
    const page=TestBed.runInInjectionContext(() => new GoogleCalendarSettingsComponent());
    page.load(); page.webhookDirty=true; page.webhookUrl='draft'; page.change('clientSecret','draft-secret'); page.load();
    expect(page.webhookUrl).toBe('draft'); expect(page.values.clientSecret).toBe('draft-secret');
  });
  it('enables manual import without a webhook but still requires valid credentials', () => {
    const page=TestBed.runInInjectionContext(() => new GoogleCalendarSettingsComponent());
    page.status={...status,webhookUrl:null,webhookHealth:'DISABLED'};
    expect(page.canSync).toBe(true);
    page.busy=true;
    expect(page.canSync).toBe(false);
    page.busy=false; page.status={...page.status,state:'ERROR'};
    expect(page.canSync).toBe(false);
  });
  it('ignores obsolete day responses and preserves the open event during refresh', () => {
    const first=new Subject<CalendarOccurrence[]>(); api.getCalendarDay.mockReturnValueOnce(first);
    const page=TestBed.runInInjectionContext(() => new CalendarCardComponent());
    page.date='2026-09-23'; page.load(); page.date='2026-09-24'; page.ngOnChanges(); first.next([]);
    expect(page.events).toEqual([sample]);
    page.selected=sample; page.visible=true; page.load();
    expect(page.visible).toBe(true); expect(page.selected?.key).toBe('one');
  });
  it('failed local deletion keeps the modal and event without showing success', () => {
    api.deleteCalendarEvent.mockReturnValue(throwError(() => new Error('offline')));
    const page=TestBed.runInInjectionContext(() => new CalendarCardComponent());
    page.date='2026-09-24'; page.load(); page.selected=sample; page.visible=true; page.remove();
    expect(page.visible).toBe(true); expect(page.events).toHaveLength(1); expect(page.deleteError).toContain('Unable to delete from Thereabout'); expect(toast.add).not.toHaveBeenCalled();
  });
  it.each([false,true])('deletes directly and shows one toast on success (recurring=%s)', recurring => {
    const response=new Subject<void>(); api.deleteCalendarEvent.mockReturnValue(response);
    const page=TestBed.runInInjectionContext(() => new CalendarCardComponent());
    const event={...sample,recurring,originalStart:recurring?sample.start:null};
    page.date='2026-09-24'; page.events=[event]; page.selected=event; page.visible=true;
    page.remove(); page.remove();
    expect(page.deleting).toBe(true); expect(page.visible).toBe(true);
    expect(api.deleteCalendarEvent).toHaveBeenCalledTimes(1);
    expect(toast.add).not.toHaveBeenCalled();
    response.next(); response.complete();
    expect(page.deleting).toBe(false); expect(page.visible).toBe(false); expect(page.events).toHaveLength(0);
    expect(toast.add).toHaveBeenCalledExactlyOnceWith({severity:'success',summary:recurring?'Occurrence deleted from Thereabout':'Event deleted from Thereabout'});
  });

});
