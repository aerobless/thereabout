import {ChangeDetectionStrategy, Component, DestroyRef, inject, Injectable, signal, Type} from '@angular/core';
import {NgComponentOutlet} from '@angular/common';
import {ActivatedRoute} from '@angular/router';
import {firstValueFrom, timeout} from 'rxjs';
import {FrontendService} from '../../../../generated/backend-api/thereabout';

@Injectable({providedIn:'root'})
export class MapsLoader {
  private readonly api=inject(FrontendService);
  private pending:Promise<void>|null=null;
  load():Promise<void> {
    if(this.pending) return this.pending;
    this.pending=firstValueFrom(this.api.getFrontendConfiguration().pipe(timeout(15000))).then(config=>new Promise<void>((resolve,reject)=> {
      const script=document.createElement('script');
      const callback='thereaboutMapsReady';
      const callbacks=window as unknown as Record<string,unknown>;
      const cleanup=()=> {clearTimeout(timer);delete callbacks[callback];};
      const fail=()=> {cleanup();script.remove();reject(new Error('Unable to load Google Maps.'));};
      const timer=setTimeout(fail,20000);
      callbacks[callback]=()=> {cleanup();resolve();};
      script.src=`https://maps.googleapis.com/maps/api/js?${new URLSearchParams({key:config.googleMapsApiKey??'',v:'weekly',loading:'async',callback})}`;
      script.async=true;script.onerror=fail;document.head.append(script);
    })).catch(error=> {this.pending=null;throw error;});
    return this.pending;
  }
}

/** Only map pages wait for Google Maps. Launcher and settings can render immediately. */
@Component({
  selector:'app-maps-page',imports:[NgComponentOutlet],
  // The existing map pages use mutable state and eager change detection.
  changeDetection:ChangeDetectionStrategy.Eager,
  template:`@if (page(); as component) { <ng-container *ngComponentOutlet="component" /> }
    @else if (error()) { <p role="alert">The map could not be loaded. <button type="button" (click)="load()">Try again</button></p> }
    @else { <p role="status">Loading your day and map…</p> }`
})
export class MapsPageComponent {
  private readonly route=inject(ActivatedRoute);
  private readonly loader=inject(MapsLoader);
  private readonly destroyRef=inject(DestroyRef);
  readonly page=signal<Type<unknown>|null>(null);
  readonly error=signal(false);
  constructor() {void this.load();}
  async load() {
    this.error.set(false);
    try {
      await this.loader.load();
      const component=this.route.snapshot.data['mapPage']==='locations'
        ? (await import('../../modules/locationhistory/locationhistory.component')).LocationhistoryComponent
        : (await import('../../modules/dayview/dayview.component')).DayviewComponent;
      if(!this.destroyRef.destroyed) this.page.set(component);
    } catch {if(!this.destroyRef.destroyed) this.error.set(true);}
  }
}
