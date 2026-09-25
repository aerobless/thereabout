import {inject} from '@angular/core';
import {CanActivateFn, Router, Routes} from '@angular/router';
import {ConfigurationComponent} from "./modules/configuration/configuration.component";
import {StatisticsComponent} from "./modules/statistics/statistics.component";
import {IdentitiesComponent} from "./modules/identities/identities.component";
import {IdentityDetailComponent} from "./modules/identities/identity-detail/identity-detail.component";
import {MessagesListComponent} from "./modules/messages/messages-list.component";

const legacyDayLink: CanActivateFn = route => route.queryParamMap.has('date')
    ? inject(Router).createUrlTree(['/dayview'], {queryParams: route.queryParams, fragment: route.fragment ?? undefined})
    : true;

export const routes: Routes = [
    {
        path: '',
        pathMatch: 'full',
        canActivate: [legacyDayLink],
        runGuardsAndResolvers: 'paramsOrQueryParamsChange',
        loadComponent: () => import('./modules/launcher/launcher.component').then(m => m.LauncherComponent)
    },
    {
        path: 'dayview',
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
        pathMatch: 'full',
        redirectTo: ({queryParams, fragment}) => inject(Router).createUrlTree(['/'], {queryParams, fragment: fragment ?? undefined})
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
