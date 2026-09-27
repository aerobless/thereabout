import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AppModalComponent } from './app-modal.component';

@Component({
  imports: [AppModalComponent],
  template: `
    <app-modal header="Example" [(visible)]="visible" [dismissible]="!busy()">
      <label>Name <input id="modal-name" /></label>
      <ng-template #footer><button (click)="visible.set(false)">Cancel</button></ng-template>
      <app-modal header="Child" [(visible)]="childVisible"><p>Child content</p></app-modal>
    </app-modal>
  `
})
class ModalHost {
  readonly visible = signal(true);
  readonly busy = signal(false);
  readonly childVisible = signal(false);
}

async function setup(busy = false) {
  await TestBed.configureTestingModule({imports: [ModalHost]}).compileComponents();
  const fixture = TestBed.createComponent(ModalHost);
  fixture.componentInstance.busy.set(busy);
  fixture.detectChanges();
  await fixture.whenStable();
  return fixture;
}

function backdropClick() {
  const mask = document.querySelector('.app-modal-mask');
  expect(mask).not.toBeNull();
  mask!.dispatchEvent(new MouseEvent('mousedown', {bubbles: true}));
}

function escape() {
  document.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape', bubbles: true}));
}

describe('AppModalComponent', () => {
  it('projects content and footer, and dismisses only clicks on the backdrop', async () => {
    const fixture = await setup();
    expect(document.querySelector('[role="dialog"]')?.getAttribute('aria-modal')).toBe('true');
    expect(document.querySelector('.p-dialog-footer')?.textContent).toContain('Cancel');
    document.querySelector('#modal-name')!.dispatchEvent(new MouseEvent('mousedown', {bubbles: true}));
    expect(fixture.componentInstance.visible()).toBe(true);
    backdropClick();
    expect(fixture.componentInstance.visible()).toBe(false);
    fixture.destroy();
  });

  it('dismisses on Escape', async () => {
    const fixture = await setup();
    escape();
    expect(fixture.componentInstance.visible()).toBe(false);
    fixture.destroy();
  });

  it('blocks backdrop and Escape during a save, then allows dismissal again', async () => {
    const fixture = await setup();
    fixture.componentInstance.busy.set(true);
    fixture.detectChanges();
    expect(document.querySelector<HTMLButtonElement>('.p-dialog-close-button')?.disabled).toBe(true);
    backdropClick();
    escape();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.componentInstance.visible()).toBe(true);
    expect(document.querySelector('[role="dialog"]')).not.toBeNull();
    fixture.componentInstance.busy.set(false);
    fixture.detectChanges();
    escape();
    expect(fixture.componentInstance.visible()).toBe(false);
    fixture.destroy();
  });
  it('enables Escape after a dialog opened in a busy state becomes available', async () => {
    const fixture = await setup(true);
    escape();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.componentInstance.visible()).toBe(true);
    fixture.componentInstance.busy.set(false);
    fixture.detectChanges();
    escape();
    expect(fixture.componentInstance.visible()).toBe(false);
    fixture.destroy();
  });

  it('closes the topmost nested dialog without closing its parent', async () => {
    const fixture = await setup();
    fixture.componentInstance.childVisible.set(true);
    fixture.detectChanges();
    await fixture.whenStable();
    const dialogs = document.querySelectorAll('[role="dialog"]');
    expect(dialogs).toHaveLength(2);
    expect(dialogs[1].querySelector('.p-dialog-footer')).toBeNull();
    escape();
    expect(fixture.componentInstance.childVisible()).toBe(false);
    expect(fixture.componentInstance.visible()).toBe(true);
    fixture.destroy();
  });

});
