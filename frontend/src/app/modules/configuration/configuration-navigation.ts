import {DestroyRef, Injectable, inject, signal} from '@angular/core';
import {CanDeactivateFn} from '@angular/router';
import {MessageService} from 'primeng/api';

export interface ConfigurationEditor {
  hasUnsavedChanges(): boolean;
  isNavigationBlocked(): boolean;
}
@Injectable()
export class ConfigurationNavigation {
  private readonly messages = inject(MessageService);
  readonly confirming = signal(false);
  private resolve?: (leave: boolean) => void;
  private pending?: Promise<boolean>;
  constructor() { inject(DestroyRef).onDestroy(() => this.answer(false)); }
  leave(editor: ConfigurationEditor): boolean | Promise<boolean> {
    if (editor.isNavigationBlocked()) {
      this.messages.add({severity: 'info', summary: 'Please wait for the current action to finish.'});
      return false;
    }
    if (!editor.hasUnsavedChanges()) return true;
    if (!this.pending) {
      this.pending = new Promise<boolean>(resolve => this.resolve = resolve);
      this.confirming.set(true);
    }
    return this.pending;
  }
  answer(leave: boolean): void {
    this.confirming.set(false);
    const resolve = this.resolve;
    this.resolve = undefined; this.pending = undefined;
    resolve?.(leave);
  }
}
export const configurationCanDeactivate: CanDeactivateFn<ConfigurationEditor> =
  component => inject(ConfigurationNavigation).leave(component);
