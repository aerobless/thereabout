import {TestBed,ComponentFixture} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {of,Subject,throwError} from 'rxjs';
import {CalendarService,HealthService,LauncherCollection,LauncherService} from '../../../../generated/backend-api/thereabout';
import {LauncherComponent} from './launcher.component';

const collection:LauncherCollection={groups:[{id:1,section:'Example',name:'Tools',position:0}],shortcuts:[
  {id:1,groupId:1,title:'Calendar',description:'Schedule',url:'https://example.org/calendar',emoji:'',position:0,iconVersion:0,hasIcon:false,iconState:'fallback'},
  {id:2,groupId:1,title:'Call notes',description:'Notes',url:'https://example.org/notes',emoji:'',position:1,iconVersion:0,hasIcon:false,iconState:'fallback'}
]};
describe('LauncherComponent',()=> {
  let fixture:ComponentFixture<LauncherComponent>;
  let api:any,health:any,calendar:any;
  const root=()=>fixture.nativeElement as HTMLElement;
  const input=()=>root().querySelector<HTMLInputElement>('#launcher-search')!;
  beforeEach(async()=> {
    Element.prototype.scrollIntoView=vi.fn();
    api={getLauncher:vi.fn(()=>of(collection)),updateLauncherShortcut:vi.fn(()=>of(collection)),createLauncherShortcut:vi.fn(()=>of(collection)),importLauncher:vi.fn(()=>of(collection)),reorderLauncherShortcuts:vi.fn(()=>of(collection))};
    health={getHealthDataByDateRange:vi.fn(()=>of({metrics:{}}))};
    calendar={getUpcomingCalendarEvent:vi.fn(()=>of([]))};
    await TestBed.configureTestingModule({imports:[LauncherComponent],providers:[provideRouter([]),{provide:LauncherService,useValue:api},{provide:HealthService,useValue:health},{provide:CalendarService,useValue:calendar}]}).compileComponents();
    fixture=TestBed.createComponent(LauncherComponent);fixture.detectChanges();
  });
  afterEach(()=>fixture.destroy());
  function key(target:HTMLElement,key:string,options:KeyboardEventInit={}) {
    const event=new KeyboardEvent('keydown',{key,bubbles:true,cancelable:true,...options});target.dispatchEvent(event);fixture.detectChanges();return event;
  }
  it('captures typing outside the search and opens the highlighted result with Enter',()=> {
    const page=fixture.componentInstance,launch=vi.spyOn(page,'launch').mockImplementation(()=>{});
    key(root(),'c');key(root(),'a');fixture.detectChanges();
    expect(page.query()).toBe('ca');expect(page.results()).toHaveLength(2);
    expect(document.activeElement).toBe(input());
    key(input(),'Tab');expect(page.activeResult()?.id).toBe(2);
    key(input(),'Enter');expect(launch).toHaveBeenCalledWith(collection.shortcuts[1]);
    expect(key(input(),'Tab').defaultPrevented).toBe(false);
    key(input(),'Escape');expect(page.query()).toBe('');
  });
  it('does not capture shortcuts, composition, or typing in editors',()=> {
    const page=fixture.componentInstance;
    key(root(),'k',{ctrlKey:true});key(root(),'a',{metaKey:true});key(root(),'x',{isComposing:true});
    expect(page.query()).toBe('');
    page.openShortcut(collection.shortcuts[0]);fixture.detectChanges();
    key(root(),'a');expect(page.query()).toBe('');
    const draftInput=document.getElementById('shortcut-title')!;
    expect(key(draftInput,'z').defaultPrevented).toBe(false);
  });
  it('renders same-tab links and a separate edit action',()=> {
    const link=root().querySelector<HTMLAnchorElement>('.shortcut-link')!;
    expect(link.href).toBe('https://example.org/calendar');expect(link.target).toBe('');
    root().querySelector<HTMLButtonElement>('.shortcut-edit')!.click();fixture.detectChanges();
    expect(fixture.componentInstance.dialog()).toBe('shortcut');
    expect(fixture.componentInstance.draft.title).toBe('Calendar');
  });
  it('keeps the editor and draft when saving fails',()=> {
    api.updateLauncherShortcut.mockReturnValue(throwError(()=>({status:500})));
    const page=fixture.componentInstance;page.openShortcut(collection.shortcuts[0]);page.draft.title='Edited';page.saveShortcut();
    expect(page.dialog()).toBe('shortcut');expect(page.draft.title).toBe('Edited');expect(page.editorError()).toContain('Unable to save');expect(page.busy()).toBe(false);
  });
  it('ignores a late collection read after a saved mutation',()=> {
    const pending=new Subject<LauncherCollection>();api.getLauncher.mockReturnValue(pending);
    const page=fixture.componentInstance;page.loadCollection();page.openShortcut(collection.shortcuts[0]);
    const updated={...collection,shortcuts:[{...collection.shortcuts[0],title:'Saved'},collection.shortcuts[1]]};
    api.updateLauncherShortcut.mockReturnValue(of(updated));page.saveShortcut();pending.next(collection);
    expect(page.collection().shortcuts[0].title).toBe('Saved');
  });
  it('distinguishes missing steps from zero and keeps launch controls on summary errors',()=> {
    const page=fixture.componentInstance;
    expect(page.steps()).toBeNull();expect(root().textContent).toContain('No steps recorded yet');
    health.getHealthDataByDateRange.mockReturnValue(of({metrics:{step_count:[{date:page.date(),qty:0}]}}));page.reload();fixture.detectChanges();
    expect(page.steps()).toBe(0);expect(root().textContent).toContain('0 steps');
    calendar.getUpcomingCalendarEvent.mockReturnValue(throwError(()=>new Error('offline')));page.reload();fixture.detectChanges();
    expect(root().textContent).toContain('Calendar unavailable');expect(root().querySelectorAll('.shortcut-link')).toHaveLength(2);
  });
  it('validates import input and only sends a valid collection to the runtime API',()=> {
    const page=fixture.componentInstance;page.importText='not JSON';page.importCollection();
    expect(api.importLauncher).not.toHaveBeenCalled();
    page.importText=JSON.stringify({groups:[{name:'Demo',section:'Example',shortcuts:[{title:'Example',url:'https://example.org'}]}]});page.importCollection();
    expect(api.importLauncher).toHaveBeenCalledTimes(1);expect(page.importText).toBe('');
  });
  it('falls back after an icon fails and retries a new icon version',()=> {
    const page=fixture.componentInstance,icon={...collection.shortcuts[0],hasIcon:true,iconVersion:1};
    page.iconFailed(icon);expect(page.hasIcon(icon)).toBe(false);expect(page.hasIcon({...icon,iconVersion:2})).toBe(true);
  });
});
