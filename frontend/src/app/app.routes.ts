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
    {path:'finances',loadComponent:()=>import('./modules/finances/finances.component').then(m=>m.FinancesComponent),children:[
      {path:'',loadComponent:()=>import('./modules/finances/pages/overview.component').then(m=>m.OverviewComponent)},
      {path:'accounts',loadComponent:()=>import('./modules/finances/pages/accounts.component').then(m=>m.AccountsComponent)},
      {path:'accounts/:id',loadComponent:()=>import('./modules/finances/pages/account-detail.component').then(m=>m.AccountDetailComponent)},
      {path:'transactions',loadComponent:()=>import('./modules/finances/pages/transactions.component').then(m=>m.TransactionsComponent)},
      {path:'reports',loadComponent:()=>import('./modules/finances/pages/reports.component').then(m=>m.ReportsComponent)}
    ]},
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
