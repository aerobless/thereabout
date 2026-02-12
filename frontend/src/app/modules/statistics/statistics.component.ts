import {Component, OnInit} from '@angular/core';
import {ButtonModule} from "primeng/button";
import {FileUploadModule} from "primeng/fileupload";
import {InputTextModule} from "primeng/inputtext";
import {NgIf} from "@angular/common";
import {PanelModule} from "primeng/panel";
import {ProgressBarModule} from "primeng/progressbar";
import {MessageService} from "primeng/api";
import {ToolbarComponent} from "../../shared/toolbar/toolbar.component";
import {CountryStatistic, FrontendService, StatisticsService} from "../../../../generated/backend-api/thereabout";
import {CardModule} from "primeng/card";
import {TableModule} from "primeng/table";
import {ReformatDatePipe} from "../../util/reformat-date.pipe";
import {getFlagEmoji} from "../../util/country-util";
import {TooltipModule} from "primeng/tooltip";

@Component({
  selector: 'app-statistics',
  standalone: true,
    imports: [
        ButtonModule,
        FileUploadModule,
        InputTextModule,
        NgIf,
        PanelModule,
        ProgressBarModule,
        ToolbarComponent,
        CardModule,
        TableModule,
        ReformatDatePipe,
        TooltipModule
    ],
  templateUrl: './statistics.component.html',
  styleUrl: './statistics.component.scss'
})
export class StatisticsComponent implements OnInit {

  visitedCountries: Array<CountryStatistic> = [];

  constructor(private messageService: MessageService, private statisticsService: StatisticsService) {
  }

  ngOnInit(): void {
    this.statisticsService.getStatistics().subscribe(statistics => {
      this.visitedCountries = statistics.visitedCountries.sort((a, b) => b.numberOfDaysSpent - a.numberOfDaysSpent);
    });
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
