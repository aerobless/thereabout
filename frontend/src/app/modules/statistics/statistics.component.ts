import {registerRefresh} from '../../shared/refresh/refresh-coordinator';
import {Component, OnInit, ChangeDetectionStrategy} from '@angular/core';
import {ButtonModule} from "primeng/button";
import {FileUploadModule} from "primeng/fileupload";
import {InputTextModule} from "primeng/inputtext";

import {PanelModule} from "primeng/panel";
import {ProgressBarModule} from "primeng/progressbar";
import {MessageService} from "primeng/api";
import {CountryStatistic, FrontendService, StatisticsService} from "../../../../generated/backend-api/thereabout";
import {CardModule} from "primeng/card";
import {TableModule} from "primeng/table";
import {ReformatDatePipe} from "../../util/reformat-date.pipe";
import {getFlagEmoji} from "../../util/country-util";
import {TooltipModule} from "primeng/tooltip";

@Component({
    selector: 'app-statistics',
    imports: [
    ButtonModule,
    FileUploadModule,
    InputTextModule,
    PanelModule,
    ProgressBarModule,
    CardModule,
    TableModule,
    ReformatDatePipe,
    TooltipModule
],
    templateUrl: './statistics.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './statistics.component.scss'
})
export class StatisticsComponent implements OnInit {
  private readonly refresh = registerRefresh(() => this.loadStatistics());

  visitedCountries: Array<CountryStatistic> = [];

  constructor(private messageService: MessageService, private statisticsService: StatisticsService) {
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

  calculateDaysSpentAbroad() {
    if (this.visitedCountries.length === 0) return 0;

    // Find the maximum number of days spent
    const maxDays = Math.max(...this.visitedCountries.map(country => country.numberOfDaysSpent));

    // Sum all days except the maximum
    return this.visitedCountries
        .filter(country => country.numberOfDaysSpent !== maxDays)
        .reduce((acc, country) => acc + country.numberOfDaysSpent, 0);
  }
}
