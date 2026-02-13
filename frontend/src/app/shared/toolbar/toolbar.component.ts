import {Component} from '@angular/core';
import {Router} from '@angular/router';
import {ToolbarModule} from 'primeng/toolbar';
import {ButtonModule} from 'primeng/button';
import {TooltipModule} from 'primeng/tooltip';
import {MenuModule} from 'primeng/menu';
import {MenuItem} from 'primeng/api';

@Component({
    selector: 'thereabout-toolbar',
    imports: [
        ToolbarModule,
        ButtonModule,
        TooltipModule,
        MenuModule,
    ],
    templateUrl: './toolbar.component.html',
    styleUrl: './toolbar.component.scss'
})
export class ToolbarComponent {

    menuItems: MenuItem[] = [
        { label: 'Day View', icon: 'pi pi-calendar', command: () => this.navigate('') },
        { label: 'Location History', icon: 'pi pi-map', command: () => this.navigate('locationhistory') },
        { label: 'Trips', icon: 'pi pi-star-fill', command: () => this.navigate('trips') },
        { label: 'Statistics', icon: 'pi pi-chart-line', command: () => this.navigate('statistics') },
        { label: 'Configuration', icon: 'pi pi-cog', command: () => this.navigate('configuration') },
    ];

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
