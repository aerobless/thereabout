import {provideZonelessChangeDetection,signal} from '@angular/core';
import {CurrentUserService} from '../../shared/current-user/current-user.service';
import {TestBed,ComponentFixture} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {CdkDrag, CdkDragDrop, CdkDropList} from '@angular/cdk/drag-drop';
import {provideRouter} from '@angular/router';
import {of,Subject,throwError} from 'rxjs';
import {CalendarService,HealthService,LauncherCollection,LauncherGroup,LauncherService,LauncherShortcut} from '../../../../generated/backend-api/thereabout';
import {LauncherComponent} from './launcher.component';

const collection:LauncherCollection={groups:[{id:1,section:'Example',name:'Tools',position:0}],shortcuts:[
  {id:1,groupId:1,title:'Calendar',description:'Schedule',url:'https://example.org/calendar',emoji:'',position:0,iconVersion:0,hasIcon:false,iconState:'fallback'},
  {id:2,groupId:1,title:'Call notes',description:'Notes',url:'https://example.org/notes',emoji:'',position:1,iconVersion:0,hasIcon:false,iconState:'fallback'}
]};
describe('LauncherComponent',()=> {
  let fixture:ComponentFixture<LauncherComponent>;
  let api:any,health:any,calendar:any;
  const currentUser = {state: signal<any>({status: 'loading'}), displayName: signal<string | undefined>(undefined)};
  const root=()=>fixture.nativeElement as HTMLElement;
  const input=()=>root().querySelector<HTMLInputElement>('#launcher-search')!;
  beforeEach(async()=> {
    currentUser.state.set({status: 'loading'}); currentUser.displayName.set(undefined);
    Element.prototype.scrollIntoView=vi.fn();
    api={getLauncher:vi.fn(()=>of(collection)),updateLauncherShortcut:vi.fn(()=>of(collection)),createLauncherShortcut:vi.fn(()=>of(collection)),importLauncher:vi.fn(()=>of(collection)),reorderLauncherShortcuts:vi.fn(()=>of(collection)),reorderLauncherGroups:vi.fn(()=>of(collection)),deleteLauncherGroup:vi.fn(()=>of({groups:[],shortcuts:[]}))};
    health={getHealthDataByDateRange:vi.fn(()=>of({metrics:{}}))};
    calendar={getUpcomingCalendarEvent:vi.fn(()=>of([]))};
    await TestBed.configureTestingModule({imports:[LauncherComponent],providers:[provideZonelessChangeDetection(),{provide:CurrentUserService,useValue:currentUser},provideRouter([]),{provide:LauncherService,useValue:api},{provide:HealthService,useValue:health},{provide:CalendarService,useValue:calendar}]}).compileComponents();
    fixture=TestBed.createComponent(LauncherComponent);fixture.detectChanges();
  });
  afterEach(()=>fixture.destroy());
  function key(target:HTMLElement,key:string,options:KeyboardEventInit={}) {
    const event=new KeyboardEvent('keydown',{key,bubbles:true,cancelable:true,...options});target.dispatchEvent(event);fixture.detectChanges();return event;
  }
  it('uses neutral loading and unknown greetings, and distinct verified names', () => {
    const heading = () => root().querySelector('h1')!.textContent!;
    expect(heading()).not.toContain('Theo');
    expect(heading()).not.toContain(',');
    currentUser.state.set({status: 'unlinked'}); fixture.detectChanges();
    expect(root().textContent).toContain('No Thereabout user assigned.');
    currentUser.state.set({status: 'resolved'}); currentUser.displayName.set('Theo'); fixture.detectChanges();
    expect(heading()).toContain(', Theo.');
    currentUser.displayName.set('Heidi'); fixture.detectChanges();
    expect(heading()).toContain(', Heidi.'); expect(heading()).not.toContain('Theo');
  });
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
  it('adds a shortcut directly to the chosen group', async () => {
    const page=fixture.componentInstance;
    page.collection.set({...collection,groups:[...collection.groups,{id:2,section:'Example',name:'Finance',position:1}]});
    await fixture.whenStable();
    root().querySelector<HTMLButtonElement>('[aria-label="Add shortcut to Finance"]')!.click();
    await fixture.whenStable();
    expect(page.dialog()).toBe('shortcut');
    expect(page.draft.groupId).toBe(2);
    expect(document.querySelector<HTMLSelectElement>('#shortcut-group')!.selectedOptions[0].textContent).toContain('Finance');
  });
  function drop(previousIndex:number,currentIndex:number,isPointerOverContainer=true):void {
    const list=fixture.debugElement.query(By.css('.organizer-shortcuts')).injector.get<CdkDropList<LauncherShortcut[]>>(CdkDropList);
    const item=fixture.debugElement.queryAll(By.css('.organizer-shortcut'))[previousIndex].injector.get<CdkDrag<LauncherShortcut>>(CdkDrag);
    const event:CdkDragDrop<LauncherShortcut[],LauncherShortcut[],LauncherShortcut>={
      previousIndex,currentIndex,item,container:list,previousContainer:list,isPointerOverContainer,
      distance:{x:0,y:40},dropPoint:{x:10,y:50},event:new MouseEvent('mouseup')
    };
    list.dropped.emit(event);
  }
  it('shows a dragged shortcut order immediately and keeps its rows after confirmation without reloading', async () => {
    const page=fixture.componentInstance,pending=new Subject<LauncherCollection>();
    api.reorderLauncherShortcuts.mockReturnValue(pending);
    page.openOrganizer(new Event('click'));await fixture.whenStable();
    const originalRow=document.querySelector('.organizer-shortcut');
    const reads=api.getLauncher.mock.calls.length;
    drop(0,1);
    expect(api.reorderLauncherShortcuts).toHaveBeenCalledWith(1,{ids:[2,1]});
    expect(page.busy()).toBe(true);
    await fixture.whenStable();
    expect(Array.from(document.querySelectorAll('.organizer-shortcut .organizer-name'),node=>node.textContent?.trim())).toEqual(['Call notes','Calendar']);
    const optimistic=page.collection();
    pending.next({...optimistic});pending.complete();
    await fixture.whenStable();
    expect(Array.from(document.querySelectorAll('.organizer-shortcut .organizer-name'),node=>node.textContent?.trim())).toEqual(['Call notes','Calendar']);
    expect(page.dialog()).toBe('organize');expect(page.busy()).toBe(false);
    expect(page.collection()).toBe(optimistic);
    expect(document.querySelectorAll('.organizer-shortcut')[1]).toBe(originalRow);
    expect(api.getLauncher).toHaveBeenCalledTimes(reads);
  });
  function dropGroup(previousIndex:number,currentIndex:number,isPointerOverContainer=true):void {
    const list=fixture.debugElement.query(By.css('.organizer-groups')).injector.get<CdkDropList<LauncherGroup[]>>(CdkDropList);
    const item=fixture.debugElement.queryAll(By.css('.organizer-group'))[previousIndex].injector.get<CdkDrag<LauncherGroup>>(CdkDrag);
    const event:CdkDragDrop<LauncherGroup[],LauncherGroup[],LauncherGroup>={
      previousIndex,currentIndex,item,container:list,previousContainer:list,isPointerOverContainer,
      distance:{x:0,y:40},dropPoint:{x:10,y:50},event:new MouseEvent('mouseup')
    };
    list.dropped.emit(event);
  }
  async function organizerWithGroups() {
    const page=fixture.componentInstance;
    page.collection.set({...collection,groups:[...collection.groups,
      {id:2,section:'Example',name:'Finance',position:1},
      {id:3,section:'Work',name:'Project',position:2}],
      shortcuts:[...collection.shortcuts,{...collection.shortcuts[0],id:3,groupId:2,title:'Bank',position:5}]});
    page.openOrganizer(new Event('click'));await fixture.whenStable();
    return page;
  }
  it('reorders groups immediately within their section and restores their order after a failed save', async () => {
    const page=await organizerWithGroups(),pending=new Subject<LauncherCollection>();
    api.reorderLauncherGroups.mockReturnValue(pending);
    dropGroup(0,2);dropGroup(0,0);dropGroup(0,1,false);
    expect(api.reorderLauncherGroups).not.toHaveBeenCalled();
    dropGroup(0,1);await fixture.whenStable();
    expect(api.reorderLauncherGroups).toHaveBeenCalledWith({ids:[2,1,3]});
    expect(page.collection().groups.map(group=>group.id)).toEqual([2,1,3]);
    expect(page.selectedGroupId()).toBe(1);
    expect(document.querySelector('.pi-arrow-up')).toBeNull();
    pending.error({status:500});await fixture.whenStable();
    expect(page.collection().groups.map(group=>group.id)).toEqual([1,2,3]);
    expect(document.querySelector('.editor-error')!.textContent).toContain('move was undone');
  });
  function transferToGroup(groupId:number,isPointerOverContainer=true):void {
    const source=fixture.debugElement.query(By.css('.organizer-shortcuts')).injector.get<CdkDropList<LauncherShortcut[]>>(CdkDropList);
    const target=fixture.debugElement.queryAll(By.css('.group-drop-target')).map(node=>node.injector.get<CdkDropList<number>>(CdkDropList)).find(list=>list.data===groupId)!;
    const item=fixture.debugElement.query(By.css('.organizer-shortcut')).injector.get<CdkDrag<LauncherShortcut>>(CdkDrag);
    const event:CdkDragDrop<number,LauncherShortcut[],LauncherShortcut>={
      previousIndex:0,currentIndex:0,item,container:target,previousContainer:source,isPointerOverContainer,
      distance:{x:-100,y:40},dropPoint:{x:10,y:50},event:new MouseEvent('mouseup')
    };
    target.dropped.emit(event);
  }
  it('keeps the drag source mounted until the drop animation ends', async () => {
    const page=await organizerWithGroups();
    const drag=fixture.debugElement.query(By.css('.organizer-shortcut')).injector.get<CdkDrag<LauncherShortcut>>(CdkDrag);
    drag.started.emit({source:drag,event:new MouseEvent('mousedown')});await fixture.whenStable();
    const groupButton=document.querySelector<HTMLButtonElement>('.organizer-group:nth-child(2) .organizer-name')!;
    expect(groupButton.getAttribute('aria-disabled')).toBe('true');
    groupButton.click();page.closeDialog();
    expect(page.selectedGroupId()).toBe(1);expect(page.dialog()).toBe('organize');
    drag.ended.emit({source:drag,distance:{x:-100,y:40},dropPoint:{x:10,y:50},event:new MouseEvent('mouseup')});
    transferToGroup(2);await fixture.whenStable();
    expect(page.organizedShortcuts().map(shortcut=>shortcut.id)).toEqual([2]);
    expect(groupButton.disabled).toBe(false);
    groupButton.click();await fixture.whenStable();
    expect(page.selectedGroupId()).toBe(2);
    expect(page.organizedShortcuts().map(shortcut=>shortcut.id)).toEqual([3,1]);
  });
  it('appends a shortcut to another group immediately without changing selection or applying the save response', async () => {
    const page=await organizerWithGroups(),pending=new Subject<LauncherCollection>();
    api.updateLauncherShortcut.mockReturnValue(pending);
    transferToGroup(1);transferToGroup(2,false);
    expect(api.updateLauncherShortcut).not.toHaveBeenCalled();
    transferToGroup(2);await fixture.whenStable();
    expect(api.updateLauncherShortcut).toHaveBeenCalledWith(1,{
      groupId:2,title:'Calendar',url:'https://example.org/calendar',description:'Schedule',emoji:''
    });
    expect(page.selectedGroupId()).toBe(1);
    expect(page.organizedShortcuts().map(shortcut=>shortcut.id)).toEqual([2]);
    expect(page.collection().shortcuts.filter(shortcut=>shortcut.groupId===2).map(shortcut=>[shortcut.id,shortcut.position])).toEqual([[3,5],[1,6]]);
    const optimistic=page.collection();pending.next({...optimistic});pending.complete();await fixture.whenStable();
    expect(page.collection()).toBe(optimistic);
    expect(page.selectedGroupId()).toBe(1);
    expect(page.dialog()).toBe('organize');
  });
  it('restores a failed transfer and allows the drag handle to reorder with the keyboard', async () => {
    const page=await organizerWithGroups(),pending=new Subject<LauncherCollection>();
    api.updateLauncherShortcut.mockReturnValue(pending);
    transferToGroup(3);await fixture.whenStable();
    expect(page.collection().shortcuts.find(shortcut=>shortcut.id===1)).toMatchObject({groupId:3,position:0});
    pending.error({status:409});await fixture.whenStable();
    expect(page.organizedShortcuts().map(shortcut=>shortcut.id)).toEqual([1,2]);
    expect(page.selectedGroupId()).toBe(1);
    expect(document.querySelector('.editor-error')!.textContent).toContain('collection changed');
    const handle=document.querySelector<HTMLButtonElement>('.organizer-shortcut .drag-handle')!;
    key(handle,'ArrowDown');
    expect(api.reorderLauncherShortcuts).toHaveBeenCalledWith(1,{ids:[2,1]});
    expect(page.organizedShortcuts().map(shortcut=>shortcut.id)).toEqual([2,1]);
  });
  it('keeps the saved order on a failed drop and skips unchanged or cancelled drops', async () => {
    const page=fixture.componentInstance;
    page.openOrganizer(new Event('click'));await fixture.whenStable();
    drop(0,0);drop(0,1,false);
    expect(api.reorderLauncherShortcuts).not.toHaveBeenCalled();
    api.reorderLauncherShortcuts.mockReturnValue(throwError(()=>({status:500})));
    drop(0,1);await fixture.whenStable();
    expect(page.organizedShortcuts().map(shortcut=>shortcut.id)).toEqual([1,2]);
    expect(document.querySelector('.editor-error')!.textContent).toContain('Unable to save');
    expect(page.dialog()).toBe('organize');
  });
  it('confirms the shortcut count before deleting a populated group and preserves it on failure', async () => {
    const page=fixture.componentInstance,pending=new Subject<LauncherCollection>();
    api.deleteLauncherGroup.mockReturnValue(pending);
    page.openGroup(collection.groups[0]);await fixture.whenStable();
    page.deleteCurrent();expect(api.deleteLauncherGroup).not.toHaveBeenCalled();
    const remove=document.querySelector<HTMLButtonElement>('.editor-footer .danger-text')!;
    expect(remove.disabled).toBe(false);remove.click();await fixture.whenStable();
    const confirmation=()=>Array.from(document.querySelectorAll<HTMLElement>('[role="dialog"]')).find(dialog=>dialog.querySelector('.p-dialog-title')?.textContent==='Delete group')!;
    expect(confirmation().textContent).toContain('Tools and its 2 shortcuts');
    document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));await fixture.whenStable();
    expect(page.confirmDelete()).toBe(false);expect(page.dialog()).toBe('group');
    expect(api.deleteLauncherGroup).not.toHaveBeenCalled();
    remove.click();await fixture.whenStable();
    confirmation().querySelector<HTMLButtonElement>('.danger-button')!.click();await fixture.whenStable();
    expect(api.deleteLauncherGroup).toHaveBeenCalledWith(1);
    expect(confirmation().querySelector<HTMLButtonElement>('.danger-button')!.disabled).toBe(true);
    pending.error({status:500});await fixture.whenStable();
    expect(page.collection()).toBe(collection);expect(page.confirmDelete()).toBe(true);
    expect(confirmation().querySelector('.editor-error')!.textContent).toContain('Unable to save');
    api.deleteLauncherGroup.mockReturnValue(of({groups:[],shortcuts:[]}));
    confirmation().querySelector<HTMLButtonElement>('.danger-button')!.click();await fixture.whenStable();
    expect(page.collection()).toEqual({groups:[],shortcuts:[]});expect(page.dialog()).toBeNull();
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
