import {Routes} from '@angular/router';
import {ConfigurationNavigation, configurationCanDeactivate} from './configuration-navigation';
import {ConnectionSummaries} from './connection-summaries';

export const configurationRoutes: Routes = [{
  path: '', providers: [ConfigurationNavigation, ConnectionSummaries],
  loadComponent: () => import('./configuration.component').then(m => m.ConfigurationComponent),
  children: [
    {path: '', pathMatch: 'full', redirectTo: 'connections/google-calendar'},
    {path: 'connections', loadComponent: () => import('./connections.component').then(m => m.ConnectionsComponent), children: [
      {path: '', pathMatch: 'full', redirectTo: 'google-calendar'},
      {path: 'google-calendar', canDeactivate: [configurationCanDeactivate], loadComponent: () => import('../calendar/google-calendar-settings.component').then(m => m.GoogleCalendarSettingsComponent)},
      {path: 'splitwise', canDeactivate: [configurationCanDeactivate], loadComponent: () => import('./splitwise-settings.component').then(m => m.SplitwiseSettingsComponent)},
      {path: 'openai', canDeactivate: [configurationCanDeactivate], loadComponent: () => import('./openai-settings.component').then(m => m.OpenAiSettingsComponent)},
      {path: 'telegram', canDeactivate: [configurationCanDeactivate], loadComponent: () => import('./telegram-settings.component').then(m => m.TelegramSettingsComponent)}
    ]},
    {path: 'location-imports', canDeactivate: [configurationCanDeactivate], loadComponent: () => import('./location-imports.component').then(m => m.LocationImportsComponent)},
    {path: 'api-access', loadComponent: () => import('./api-access.component').then(m => m.ApiAccessComponent)},
    {path: 'finances', canDeactivate: [configurationCanDeactivate], loadComponent: () => import('./finance-settings.component').then(m => m.FinanceSettingsComponent)},
    {path: 'about', loadComponent: () => import('./about.component').then(m => m.AboutComponent)}
  ]
}];
