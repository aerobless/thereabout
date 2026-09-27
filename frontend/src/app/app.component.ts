import {Component, ChangeDetectionStrategy, inject} from '@angular/core';
import {CurrentUserService} from './shared/current-user/current-user.service';
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
  readonly currentUser = inject(CurrentUserService);
  title = 'thereabout';
}
