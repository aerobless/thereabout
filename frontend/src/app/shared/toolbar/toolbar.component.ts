import {Component} from '@angular/core';
import {Router} from '@angular/router';
import {ToolbarModule} from 'primeng/toolbar';
import {ButtonModule} from 'primeng/button';
import {TooltipModule} from 'primeng/tooltip';

@Component({
    selector: 'thereabout-toolbar',
    imports: [
        ToolbarModule,
        ButtonModule,
        TooltipModule,
    ],
    templateUrl: './toolbar.component.html',
    styleUrl: './toolbar.component.scss'
})
export class ToolbarComponent {

    constructor(private router: Router) {
    }

    navigateHome() {
        this.router.navigate(['']);
    }

    navigate(route: string) {
        this.router.navigate([route]);
    }

    isActive(route: string): boolean {
        const url = this.router.url.split('?')[0]; // strip query params
        if (route === '') {
            return url === '/' || url === '';
        }
        return url === '/' + route;
    }
}
