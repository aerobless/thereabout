import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable, shareReplay} from 'rxjs';
import {CurrentUserService} from './current-user.service';
import {UserSelectionService} from './user-selection.service';

@Injectable({providedIn: 'root'})
export class ProtectedImageCache {
  private readonly http = inject(HttpClient);
  private readonly current = inject(CurrentUserService);
  private readonly images = new Map<string, Observable<Blob>>();
  constructor() { inject(UserSelectionService).changed.subscribe(() => this.images.clear()); }
  get(url: string): Observable<Blob> {
    const state=this.current.state();
    const key=`${state.status === 'loading' ? 'loading' : state.identityId ?? 'none'}:${url}`;
    let image=this.images.get(key);
    if (!image) {
      image=this.http.get(url,{responseType: 'blob'}).pipe(shareReplay({bufferSize: 1,refCount: true}));
      this.images.set(key,image);
    }
    return image;
  }
}
