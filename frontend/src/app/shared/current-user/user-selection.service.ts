import {Injectable, signal} from '@angular/core';
import {Subject} from 'rxjs';

/** Tab-local selection; the backend authenticates and authorizes every selected-user request. */
@Injectable({providedIn: 'root'})
export class UserSelectionService {
  readonly target = signal<number | null>(null);
  readonly changed = new Subject<void>();
  select(id: number | null): void {
    this.target.set(id);
    this.changed.next();
  }
}
