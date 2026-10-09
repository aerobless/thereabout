import {inject} from '@angular/core';
import {CurrentUserService} from './shared/current-user/current-user.service';
import {CanActivateFn, Router, Routes} from '@angular/router';

const legacyDayLink: CanActivateFn = route => route.queryParamMap.has('date')
    ? inject(Router).createUrlTree(['/dayview'], {queryParams: route.queryParams, fragment: route.fragment ?? undefined})
    : true;

export const adminOnly: CanActivateFn = async () => {
    const user = inject(CurrentUserService);
    const router = inject(Router);
    await user.ready();
    return user.canManageUsers() || router.createUrlTree(['/']);
};

export const routes: Routes = [
    {path:'finances',loadComponent:()=>import('./modules/finances/finances.component').then(m=>m.FinancesComponent),children:[
      {path:'',loadComponent:()=>import('./modules/finances/pages/overview.component').then(m=>m.OverviewComponent)},
      {path:'accounts',loadComponent:()=>import('./modules/finances/pages/accounts.component').then(m=>m.AccountsComponent)},
      {path:'accounts/:id',loadComponent:()=>import('./modules/finances/pages/account-detail.component').then(m=>m.AccountDetailComponent)},
      {path:'counterparties/:id',loadComponent:()=>import('./modules/finances/pages/counterparty-detail.component').then(m=>m.CounterpartyDetailComponent)},
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
        canActivate: [adminOnly],
        canActivateChild: [adminOnly],
        loadChildren: () => import('./modules/configuration/configuration.routes').then(m => m.configurationRoutes)
    },
    {
        path: 'identities',
        canActivate: [adminOnly],
        canActivateChild: [adminOnly],
        loadComponent: () => import('./modules/identities/identities-shell.component').then(m => m.IdentitiesShellComponent),
        children: [
            {path:'', pathMatch:'full', loadComponent: () => import('./modules/identities/identities.component').then(m => m.IdentitiesComponent)},
            {path:'groups', data:{isGroup:true}, loadComponent: () => import('./modules/identities/identities.component').then(m => m.IdentitiesComponent)},
            {path:':id', loadComponent: () => import('./modules/identities/identity-detail/identity-detail.component').then(m => m.IdentityDetailComponent)},
        ]
    },
    {
        path: 'messages',
        loadComponent: () => import('./modules/messages/messages-list.component').then(m => m.MessagesListComponent)
    },
];
