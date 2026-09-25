import {afterNextRender, ChangeDetectionStrategy, Component, computed, DestroyRef, ElementRef, HostListener, inject, signal, viewChild} from '@angular/core';
import {DatePipe, DecimalPipe, NgTemplateOutlet} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {RouterLink} from '@angular/router';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {DialogModule} from 'primeng/dialog';
import {finalize, interval, Observable, of, switchMap} from 'rxjs';
import {CalendarOccurrence, CalendarService, HealthService, LauncherCollection, LauncherGroup, LauncherImport, LauncherService, LauncherShortcut, LauncherShortcutInput} from '../../../../generated/backend-api/thereabout';
import {registerRefresh} from '../../shared/refresh/refresh-coordinator';
import {dailyStepTotals} from '../dayview/steps-progress';
import {greeting, localDate, searchShortcuts, shortcutDomain} from './launcher-search';

type DialogMode='shortcut'|'group'|'organize'|'import'|null;
interface ShortcutDraft extends LauncherShortcutInput {id?: number}

@Component({
  selector:'app-launcher',
  imports:[FormsModule,RouterLink,DatePipe,DecimalPipe,DialogModule,NgTemplateOutlet],
  templateUrl:'./launcher.component.html',
  styleUrls:['./launcher.component.scss','./launcher-editor.scss'],
  changeDetection:ChangeDetectionStrategy.OnPush
})
export class LauncherComponent {
  private readonly api=inject(LauncherService);
  private readonly health=inject(HealthService);
  private readonly calendar=inject(CalendarService);
  private readonly destroyRef=inject(DestroyRef);
  private readonly refresh=registerRefresh(()=>this.reload(),()=>!!this.dialog() || this.busy());
  readonly searchInput=viewChild<ElementRef<HTMLInputElement>>('searchInput');
  readonly collection=signal<LauncherCollection>({groups:[],shortcuts:[]});
  readonly loading=signal(true);
  readonly error=signal('');
  readonly now=signal(new Date());
  readonly greeting=computed(()=>greeting(this.now()));
  readonly date=computed(()=>localDate(this.now()));
  readonly steps=signal<number|null>(null);
  readonly stepsState=signal<'loading'|'ready'|'error'>('loading');
  readonly event=signal<CalendarOccurrence|null>(null);
  readonly eventState=signal<'loading'|'ready'|'error'>('loading');
  readonly query=signal('');
  readonly results=computed(()=>searchShortcuts(this.collection(),this.query()));
  readonly activeIndex=signal(0);
  readonly activeResult=computed(()=>this.results()[Math.min(this.activeIndex(),this.results().length-1)]);
  readonly sections=computed(()=> {
    const sections=new Map<string,Array<LauncherGroup & {shortcuts:LauncherShortcut[]}>>();
    for(const group of this.collection().groups) {
      const groups=sections.get(group.section) ?? [];
      groups.push({...group,shortcuts:this.collection().shortcuts.filter(s=>s.groupId===group.id)});
      sections.set(group.section,groups);
    }
    return Array.from(sections,([name,groups])=>({name,groups}));
  });
  readonly dialog=signal<DialogMode>(null);
  readonly busy=signal(false);
  readonly editorError=signal('');
  readonly confirmDelete=signal(false);
  readonly selectedGroupId=signal<number|null>(null);
  readonly organizedShortcuts=computed(()=>this.collection().shortcuts.filter(s=>s.groupId===this.selectedGroupId()));
  readonly currentShortcut=computed(()=>this.collection().shortcuts.find(s=>s.id===this.editingId()));
  readonly editingId=signal<number|undefined>(undefined);
  readonly failedIcons=signal(new Set<string>());
  readonly imagePreview=signal('');
  draft:ShortcutDraft={groupId:0,title:'',url:'',description:'',emoji:''};
  groupDraft:{id?:number;section:string;name:string}={section:'',name:''};
  importText='';
  private imageFile:File|null=null;
  private trigger:HTMLElement|null=null;
  private fromOrganizer=false;
  private generation=0;
  private lastSummaryAt=0;
  private lastCollectionAt=0;
  readonly domain=shortcutDomain;

  constructor() {
    this.reload();
    interval(5000).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(()=> {
      if(document.hidden) return;
      const previous=this.date(); this.now.set(new Date());
      if(this.busy()) return;
      if(this.dialog()) {if(this.collection().shortcuts.some(s=>s.iconState==='pending')) this.loadCollection();return;}
      if(previous!==this.date()) { this.steps.set(null); this.stepsState.set('loading'); this.loadSummary(); }
      else if(Date.now()-this.lastSummaryAt>=30000) this.loadSummary();
      if(this.collection().shortcuts.some(s=>s.iconState==='pending') || Date.now()-this.lastCollectionAt>=60000) this.loadCollection();
    });
    afterNextRender(()=> {
      if(window.matchMedia?.('(pointer: fine)').matches) this.searchInput()?.nativeElement.focus({preventScroll:true});
    });
    this.destroyRef.onDestroy(()=>this.releasePreview());
  }

  reload() { this.now.set(new Date()); this.loadCollection(); this.loadSummary(); }
  loadCollection() {
    const generation=this.generation;
    this.lastCollectionAt=Date.now();
    this.api.getLauncher().pipe(this.refresh.track('launcher'),takeUntilDestroyed(this.destroyRef)).subscribe({
      next:data=> {if(generation===this.generation) {this.collection.set(data);this.loading.set(false);this.error.set('');}},
      error:()=> {if(generation===this.generation) {this.loading.set(false);this.error.set('Your shortcuts could not be loaded.');}}
    });
  }
  private loadSummary() {
    const date=this.date(); this.lastSummaryAt=Date.now();
    this.health.getHealthDataByDateRange(date,date).pipe(this.refresh.track('steps'),takeUntilDestroyed(this.destroyRef)).subscribe({
      next:data=> {if(date===this.date()) {this.steps.set(dailyStepTotals(data.metrics?.['step_count']??[]).get(date)??null);this.stepsState.set('ready');}},
      error:()=> {if(date===this.date()) this.stepsState.set('error');}
    });
    this.calendar.getUpcomingCalendarEvent(Intl.DateTimeFormat().resolvedOptions().timeZone)
      .pipe(this.refresh.track('next-event'),takeUntilDestroyed(this.destroyRef)).subscribe({
        next:events=> {this.event.set(events[0]??null);this.eventState.set('ready');},
        error:()=>this.eventState.set('error')
      });
  }
  @HostListener('window:focus') @HostListener('document:visibilitychange')
  onReturn() {
    if(!document.hidden && !this.busy() && !this.dialog() && Date.now()-this.lastSummaryAt>15000) this.reload();
  }
  setQuery(value:string) {this.query.set(value);this.activeIndex.set(0);}
  @HostListener('document:keydown',['$event'])
  onKey(event:KeyboardEvent) {
    if(this.dialog() || event.defaultPrevented || event.isComposing || event.ctrlKey || event.metaKey || event.altKey) return;
    const target=event.target as HTMLElement|null;
    const input=this.searchInput()?.nativeElement;
    if(target===input) {
      if(event.key==='Escape') {event.preventDefault();this.setQuery('');return;}
      if(this.results().length) {
        const direction=event.key==='ArrowDown'?1:event.key==='ArrowUp'?-1:event.key==='Tab'?(event.shiftKey?-1:1):0;
        if(direction) {
          const next=this.activeIndex()+direction;
          if(event.key==='Tab' && (next<0 || next>=this.results().length)) return;
          event.preventDefault();this.activeIndex.set((next+this.results().length)%this.results().length);
          document.getElementById(`launcher-result-${this.activeResult()?.id}`)?.scrollIntoView({block:'nearest'});
        } else if(event.key==='Enter' && this.activeResult()) {event.preventDefault();this.launch(this.activeResult()!);}
      }
      return;
    }
    if(target?.closest('input,textarea,select,[contenteditable="true"],[role="textbox"]')) return;
    if(event.key.length===1 && (event.key!==' ' || !target?.closest('a,button'))) {
      event.preventDefault();input?.focus({preventScroll:true});this.setQuery(this.query()+event.key);
    }
  }
  launch(shortcut:LauncherShortcut) {window.location.assign(shortcut.url);}
  iconUrl(shortcut:LauncherShortcut) {return `/backend/api/v1/launcher/shortcuts/${shortcut.id}/icon?v=${shortcut.iconVersion}`;}
  hasIcon(shortcut:LauncherShortcut) {return shortcut.hasIcon && !this.failedIcons().has(`${shortcut.id}:${shortcut.iconVersion}`);}
  iconFailed(shortcut:LauncherShortcut) {this.failedIcons.update(failed=>new Set([...failed,`${shortcut.id}:${shortcut.iconVersion}`]));}
  groupLabel(id:number) {const g=this.collection().groups.find(g=>g.id===id);return g?`${g.section} · ${g.name}`:'';}
  eventWhen(event:CalendarOccurrence) {
    const start=new Date(event.start),diff=Math.ceil((start.getTime()-this.now().getTime())/60000);
    if(localDate(start)===this.date()) return diff>0?`In ${diff<60?`${diff} min`:`${Math.floor(diff/60)} h ${diff%60} min`}`:'Starting now';
    const tomorrow=new Date(this.now());tomorrow.setDate(tomorrow.getDate()+1);
    return localDate(start)===localDate(tomorrow)?'Tomorrow':new Intl.DateTimeFormat(undefined,{weekday:'short',day:'numeric',month:'short'}).format(start);
  }

  openOrganizer(event:Event) {this.beginDialog('organize',event);this.selectedGroupId.set(this.collection().groups[0]?.id??null);}
  openImport(event:Event) {this.beginDialog('import',event);this.importText='';}
  openShortcut(shortcut?:LauncherShortcut,event?:Event,groupId?:number) {
    this.fromOrganizer=this.dialog()==='organize';this.beginDialog('shortcut',event);
    this.editingId.set(shortcut?.id);
    this.draft=shortcut?{id:shortcut.id,groupId:shortcut.groupId,title:shortcut.title,url:shortcut.url,description:shortcut.description,emoji:shortcut.emoji}
      :{groupId:groupId??this.selectedGroupId()??this.collection().groups[0]?.id??0,title:'',url:'',description:'',emoji:''};
  }
  openGroup(group?:LauncherGroup,event?:Event) {
    this.fromOrganizer=this.dialog()==='organize';this.beginDialog('group',event);
    this.groupDraft=group?{id:group.id,name:group.name,section:group.section}:{name:'',section:this.collection().groups[0]?.section??'Personal'};
  }
  private beginDialog(mode:DialogMode,event?:Event) {
    if(event) this.trigger=event.currentTarget as HTMLElement;
    this.releasePreview();this.dialog.set(mode);this.editorError.set('');this.confirmDelete.set(false);
  }
  closeDialog() {
    if(this.busy()) return;
    this.dialog.set(null);this.releasePreview();this.editorError.set('');this.confirmDelete.set(false);
    (this.trigger?.isConnected?this.trigger:this.searchInput()?.nativeElement)?.focus({preventScroll:true});
  }
  backToOrganizer() {this.dialog.set('organize');this.releasePreview();this.editorError.set('');this.confirmDelete.set(false);}
  private saved() {if(this.fromOrganizer) this.backToOrganizer();else this.closeDialog();}
  private apply(data:LauncherCollection) {
    this.collection.set(data);this.error.set('');
    if(!data.groups.some(g=>g.id===this.selectedGroupId())) this.selectedGroupId.set(data.groups[0]?.id??null);
  }
  private mutate(request:Observable<LauncherCollection>,done?:()=>void) {
    if(this.busy()) return;
    this.busy.set(true);this.editorError.set('');++this.generation;this.refresh.cancel();
    request.pipe(finalize(()=>this.busy.set(false)),takeUntilDestroyed(this.destroyRef)).subscribe({
      next:data=> {this.apply(data);this.busy.set(false);done?.();},
      error:error=>this.editorError.set(error.status===409?'The collection changed or this group still contains shortcuts. Refresh and try again.':
        error.status===400?'Please check the URL, required fields, and image format.':'Unable to save. Your changes are still here; please try again.')
    });
  }
  saveShortcut() {
    if(!this.draft.title.trim() || !this.draft.groupId) {this.editorError.set('Enter a name and choose a group.');return;}
    try {const url=new URL(this.draft.url);if(!['http:','https:'].includes(url.protocol)||url.username||url.password) throw new Error();}
    catch {this.editorError.set('Enter a complete http:// or https:// URL without credentials.');return;}
    const ids=new Set(this.collection().shortcuts.map(s=>s.id));
    const request=this.draft.id?this.api.updateLauncherShortcut(this.draft.id,this.draft):this.api.createLauncherShortcut(this.draft);
    this.mutate(request.pipe(switchMap(data=> {
      this.apply(data);
      const id=this.draft.id??data.shortcuts.find(s=>!ids.has(s.id))?.id;
      this.draft.id=id;this.editingId.set(id);
      return this.imageFile && id?this.api.uploadLauncherIcon(id,this.imageFile):of(data);
    })),()=>this.saved());
  }
  saveGroup() {
    if(!this.groupDraft.name.trim()||!this.groupDraft.section.trim()) {this.editorError.set('Enter a section and a group name.');return;}
    const request=this.groupDraft.id?this.api.updateLauncherGroup(this.groupDraft.id,this.groupDraft):this.api.createLauncherGroup(this.groupDraft);
    this.mutate(request,()=>this.saved());
  }
  deleteCurrent() {
    if(this.dialog()==='shortcut' && this.draft.id) this.mutate(this.api.deleteLauncherShortcut(this.draft.id),()=>this.saved());
    else if(this.dialog()==='group' && this.groupDraft.id) this.mutate(this.api.deleteLauncherGroup(this.groupDraft.id),()=>this.saved());
  }
  groupHasShortcuts(id?:number) {return this.collection().shortcuts.some(s=>s.groupId===id);}
  canMoveGroup(group:LauncherGroup,direction:number) {
    const siblings=this.collection().groups.filter(g=>g.section===group.section);
    return !!siblings[siblings.findIndex(g=>g.id===group.id)+direction];
  }
  moveGroup(group:LauncherGroup,direction:number) {
    const groups=this.collection().groups.slice(),siblings=groups.filter(g=>g.section===group.section);
    const sibling=siblings[siblings.findIndex(g=>g.id===group.id)+direction];
    if(!sibling) return;
    const i=groups.findIndex(g=>g.id===group.id),next=groups.findIndex(g=>g.id===sibling.id);
    [groups[i],groups[next]]=[groups[next],groups[i]];
    this.mutate(this.api.reorderLauncherGroups({ids:groups.map(g=>g.id)}));
  }
  moveShortcut(shortcut:LauncherShortcut,direction:number) {
    const shortcuts=this.organizedShortcuts().slice(),i=shortcuts.findIndex(s=>s.id===shortcut.id),next=i+direction;
    if(next<0||next>=shortcuts.length) return;
    [shortcuts[i],shortcuts[next]]=[shortcuts[next],shortcuts[i]];
    this.mutate(this.api.reorderLauncherShortcuts(shortcut.groupId,{ids:shortcuts.map(s=>s.id)}));
  }
  selectImage(event:Event) {
    const file=(event.target as HTMLInputElement).files?.[0];if(!file) return;
    if(file.size>1_048_576) {this.editorError.set('Choose an image up to 1 MB.');return;}
    this.releasePreview();this.imageFile=file;this.imagePreview.set(URL.createObjectURL(file));this.editorError.set('');
  }
  refreshIcon() {if(this.draft.id) {this.releasePreview();this.mutate(this.api.refreshLauncherIcon(this.draft.id));}}
  private releasePreview() {if(this.imagePreview()) URL.revokeObjectURL(this.imagePreview());this.imagePreview.set('');this.imageFile=null;}
  importCollection() {
    let data:LauncherImport;
    try {
      data=JSON.parse(this.importText);
      if(!Array.isArray(data.groups) || !data.groups.length || data.groups.some(g=>!g.name||!g.section||!Array.isArray(g.shortcuts))) throw new Error();
    } catch {this.editorError.set('Paste a collection with groups, each containing a section, name, and shortcuts list.');return;}
    this.mutate(this.api.importLauncher(data),()=>{this.importText='';this.closeDialog();});
  }
  dialogTitle() {
    switch(this.dialog()) {case 'shortcut':return this.draft.id?'Edit shortcut':'Add shortcut';case 'group':return this.groupDraft.id?'Edit group':'Add group';case 'organize':return 'Organize shortcuts';default:return 'Import shortcuts';}
  }
}
