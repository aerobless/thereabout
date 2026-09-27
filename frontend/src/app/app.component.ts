import {Component, ChangeDetectionStrategy, inject, HostListener} from '@angular/core';
import {CurrentUserService} from './shared/users/current-user.service';
import { RouterOutlet } from '@angular/router';
import {ToastModule} from "primeng/toast";
import {AppShellComponent} from './shared/app-shell/app-shell.component';

@Component({
    selector: 'app-root',
    imports: [RouterOutlet, ToastModule, AppShellComponent],
    templateUrl: './app.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './app.component.scss'
})
export class AppComponent {
  title = 'thereabout';
  private readonly currentUser = inject(CurrentUserService);
  constructor() { this.currentUser.refresh(); }
  @HostListener('window:focus') refreshUser(): void { this.currentUser.refresh(); }
}
