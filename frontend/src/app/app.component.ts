import {Component, ChangeDetectionStrategy} from '@angular/core';
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
}
