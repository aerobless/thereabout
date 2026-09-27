import {registerRefresh} from '../../shared/refresh/refresh-coordinator';
import {Component, OnInit, ChangeDetectionStrategy} from '@angular/core';

import {PanelModule} from "primeng/panel";
import {CountryStatistic, StatisticsService} from "../../../../generated/backend-api/thereabout";
import {TableModule} from "primeng/table";
import {ReformatDatePipe} from "../../util/reformat-date.pipe";
import {getFlagEmoji} from "../../util/country-util";

@Component({
    selector: 'app-statistics',
    imports: [
    PanelModule,
    TableModule,
    ReformatDatePipe
],
    templateUrl: './statistics.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './statistics.component.scss'
})
export class StatisticsComponent implements OnInit {
  private readonly refresh = registerRefresh(() => this.loadStatistics());

  visitedCountries: Array<CountryStatistic> = [];

  constructor(private statisticsService: StatisticsService) {
  }

  ngOnInit(): void { this.loadStatistics(); }

  private loadStatistics() {
    this.statisticsService.getStatistics().pipe(this.refresh.track('statistics')).subscribe({next: statistics => {
      this.visitedCountries = statistics.visitedCountries.sort((a, b) => b.numberOfDaysSpent - a.numberOfDaysSpent);
    }, error: () => { /* The refresh coordinator reports failed reads. */ }});
  }

  countryNameFormat(countryStats: CountryStatistic): string {
    return `${getFlagEmoji(countryStats.countryIsoCode)} ${countryStats.countryName}`;
  }

  mapContinent(continent: string): string {
    // EU, NA, OC, AS, AF
    switch (continent) {
      case 'EU':
        return 'Europe';
      case 'NA':
        return 'North America';
      case 'SA':
        return 'South America';
      case 'OC':
        return 'Oceania';
      case 'AS':
        return 'Asia';
      case 'AF':
        return 'Africa';
      case 'AN':
        return 'Antarctica';
      default:
        return continent;
    }
  }

}
