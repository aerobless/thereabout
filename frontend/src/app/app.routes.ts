import { Routes } from '@angular/router';
import {ConfigurationComponent} from "./modules/configuration/configuration.component";
import {LocationhistoryComponent} from "./modules/locationhistory/locationhistory.component";
import {StatisticsComponent} from "./modules/statistics/statistics.component";
import {TripsComponent} from "./modules/trips/trips.component";
import {DayviewComponent} from "./modules/dayview/dayview.component";

export const routes: Routes = [
    {
        path: '',
        component: DayviewComponent
    },
    {
        path: 'locationhistory',
        component: LocationhistoryComponent
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
        path: 'trips',
        component: TripsComponent
    },
];
