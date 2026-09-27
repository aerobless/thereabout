import { ChangeDetectionStrategy, Component, computed, contentChild, input, model, output, TemplateRef } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { Dialog, DialogModule, DialogPassThrough } from 'primeng/dialog';

@Component({
  selector: 'app-modal',
  imports: [DialogModule, NgTemplateOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-dialog #dialog [header]="header()" [visible]="visible()" (visibleChange)="changeVisibility($event, dialog)"
      [modal]="true" [draggable]="false" [resizable]="false" [blockScroll]="true" [focusTrap]="true"
      [closable]="true" [closeOnEscape]="true" [pt]="passThrough()" [dismissableMask]="dismissible()"
      appendTo="body" [styleClass]="classes()" maskStyleClass="app-modal-mask"
      [style]="dialogStyle()" [closeAriaLabel]="closeAriaLabel()"
      (onShow)="onShow.emit()" (onHide)="onHide.emit()">
      <ng-content />
      @if (footer(); as template) {
        <ng-template #footer><ng-container [ngTemplateOutlet]="template" /></ng-template>
      }
    </p-dialog>
  `
})
export class AppModalComponent {
  readonly header = input('');
  readonly visible = model(false);
  // Lock every dismissal path together while an operation or child editor is active.
  readonly dismissible = input(true);
  readonly closeAriaLabel = input('Close');
  readonly styleClass = input('');
  readonly dialogStyle = input<Record<string, string | number>>({width: '40rem', maxWidth: 'calc(100vw - 2rem)'});
  readonly onShow = output<void>();
  readonly onHide = output<void>();
  readonly footer = contentChild<TemplateRef<unknown>>('footer', {descendants: false});
  readonly passThrough = computed<DialogPassThrough>(() => ({
    pcCloseButton: {root: {disabled: !this.dismissible()}}
  }));

  changeVisibility(visible: boolean, dialog: Dialog): void {
    // PrimeNG's Escape listener captures the initial closable state. Guard the
    // model too, so a save started after opening cannot be dismissed by Escape.
    if (!visible && !this.dismissible()) {
      dialog.visible.set(true);
      return;
    }
    this.visible.set(visible);
  }

  readonly classes = computed(() => `app-modal ${this.styleClass()}`.trim());
}
