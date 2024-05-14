import { Routes } from '@angular/router';
import {ConfigurationComponent} from "./modules/configuration/configuration.component";
import {LocationhistoryComponent} from "./modules/locationhistory/locationhistory.component";

export const routes: Routes = [
    {
        path: '',
        component: LocationhistoryComponent
    },
    {
        path: 'configuration',
        component: ConfigurationComponent
    },
];
