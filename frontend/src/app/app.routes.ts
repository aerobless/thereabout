import { Routes } from '@angular/router';
import {ConfigurationComponent} from "./modules/configuration/configuration.component";
import {StatisticsComponent} from "./modules/statistics/statistics.component";
import {IdentitiesComponent} from "./modules/identities/identities.component";
import {IdentityDetailComponent} from "./modules/identities/identity-detail/identity-detail.component";
import {MessagesListComponent} from "./modules/messages/messages-list.component";

export const routes: Routes = [
    {
        path: '',
        loadComponent: () => import('./shared/maps/maps-page.component').then(m => m.MapsPageComponent),
        data: {mapPage: 'day'}
    },
    {
        path: 'locationhistory',
        loadComponent: () => import('./shared/maps/maps-page.component').then(m => m.MapsPageComponent),
        data: {mapPage: 'locations'}
    },
    {
        path: 'launcher',
        loadComponent: () => import('./modules/launcher/launcher.component').then(m => m.LauncherComponent)
    },
    {
        path: 'configuration',
        component: ConfigurationComponent
    },
    {
        path: 'statistics',
        component: StatisticsComponent
    },
    {
        path: 'identities',
        component: IdentitiesComponent
    },
    {
        path: 'identities/:id',
        component: IdentityDetailComponent
    },
    {
        path: 'messages',
        component: MessagesListComponent
    },
];
