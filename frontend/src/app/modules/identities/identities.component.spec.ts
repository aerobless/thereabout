import {provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {ActivatedRoute, provideRouter} from '@angular/router';
import {of, Subject} from 'rxjs';
import {Identity, IdentityInApplicationService, IdentityService} from '../../../../generated/backend-api/thereabout';
import {IdentitiesComponent} from './identities.component';

describe('IdentitiesComponent table', () => {
  it('uses full names for users and contacts, sorts equal first names by surname and searches the full name', async () => {
    await TestBed.configureTestingModule({imports:[IdentitiesComponent],providers:[
      provideZonelessChangeDetection(),provideRouter([]),
      {provide:IdentityService,useValue:{getIdentities:()=>of([
        {id:1,firstName:'Theo',lastName:'Winter',role:'USER'},
        {id:2,firstName:'Theo',lastName:'Adams'},
        {id:3,firstName:'Family Group Chat',lastName:'',isGroup:true}
      ])}},
      {provide:IdentityInApplicationService,useValue:{getUnlinkedIdentityInApplications:()=>of([])}}
    ]}).compileComponents();
    const fixture=TestBed.createComponent(IdentitiesComponent);
    await fixture.whenStable();
    const element=fixture.nativeElement as HTMLElement;
    const names=()=>Array.from(element.querySelectorAll('.identity-table tbody tr td:first-child'),cell=>cell.textContent?.trim());
    expect(names()).toEqual(['Theo Adams','Theo Winter']);
    const search=element.querySelector<HTMLInputElement>('input[aria-label="Search identities"]')!;
    search.value='Theo Winter';search.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    await vi.waitFor(() => expect(names()).toEqual(['Theo Winter']));
    search.value='Adams';search.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    await vi.waitFor(() => expect(names()).toEqual(['Theo Adams']));
    fixture.destroy();
  });
  it('renders roles in their own column and sorts numeric app counts across pages after a delayed load', async () => {
    const response=new Subject<Identity[]>();
    await TestBed.configureTestingModule({imports:[IdentitiesComponent],providers:[
      provideZonelessChangeDetection(),provideRouter([]),
      {provide:IdentityService,useValue:{getIdentities:()=>response}},
      {provide:IdentityInApplicationService,useValue:{getUnlinkedIdentityInApplications:()=>of([])}}
    ]}).compileComponents();
    const fixture=TestBed.createComponent(IdentitiesComponent);
    await fixture.whenStable();
    const identities:Identity[]=Array.from({length:18},(_,index)=>({
      id:index+1,firstName:`Person ${String(index).padStart(2,'0')}`,
      lastName:index===1?'Winter':'',
      role:index===1?'USER':index===2?'ADMIN':null,
      identityInApplications:Array.from({length:index},(_,app)=>({id:index*100+app,application:'Cloudflare',identifier:'example@example.org'}))
    }));
    response.next(identities);await fixture.whenStable();
    const table=(fixture.nativeElement as HTMLElement).querySelector('.identity-table')!;
    expect(Array.from(table.querySelectorAll('th'),cell=>cell.textContent?.trim())).toEqual(['Name 1','Role','Relationship','App Identities']);
    const user=table.querySelectorAll('tbody tr')[1];
    expect(user.querySelectorAll('td')[0].textContent).toBe('Person 01 Winter');
    expect(user.querySelectorAll('td')[1].textContent).toBe('User');
    expect(table.textContent).not.toContain('Cloudflare:');
    const counts=()=>Array.from(table.querySelectorAll('td[data-label="App identities"]'),cell=>Number(cell.textContent));
    const header=table.querySelector<HTMLElement>('th[psortablecolumn="appIdentityCount"]')!;
    header.click();await fixture.whenStable();
    expect(counts()).toEqual(Array.from({length:15},(_,index)=>index));
    header.click();await fixture.whenStable();
    expect(counts()).toEqual(Array.from({length:15},(_,index)=>17-index));
    fixture.destroy();
  });
  it('shows groups and their imported app IDs separately, with group-only link choices', async () => {
    await TestBed.configureTestingModule({imports:[IdentitiesComponent], providers:[
      provideZonelessChangeDetection(), provideRouter([]),
      {provide:ActivatedRoute, useValue:{data:of({isGroup:true})}},
      {provide:IdentityService,useValue:{getIdentities:()=>of([
        {id:1,firstName:'Theo',lastName:'Winter',role:'USER'},
        {id:2,firstName:'Full Group Name',lastName:'',isGroup:true},
      ])}},
      {provide:IdentityInApplicationService,useValue:{getUnlinkedIdentityInApplications:()=>of([
        {id:4,application:'WhatsApp',identifier:'Person app',isGroup:false},
        {id:5,application:'WhatsApp',identifier:'Group app',isGroup:true},
      ])}},
    ]}).compileComponents();
    const fixture = TestBed.createComponent(IdentitiesComponent);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Full Group Name');
    expect(element.textContent).toContain('Group app');
    expect(element.textContent).not.toContain('Theo Winter');
    expect(element.textContent).not.toContain('Person app');
    expect(fixture.componentInstance.identities.map(identity=>identity.id)).toEqual([2]);
    expect(element.querySelectorAll('th')).not.toContainEqual(expect.objectContaining({textContent:'Group'}));
    fixture.destroy();
  });

});
