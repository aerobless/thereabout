import {Component, OnInit} from '@angular/core';
import {ButtonModule} from "primeng/button";
import {FileUploadModule} from "primeng/fileupload";
import {InputTextModule} from "primeng/inputtext";
import {NgIf} from "@angular/common";
import {PanelModule} from "primeng/panel";
import {ProgressBarModule} from "primeng/progressbar";
import {ToolbarModule} from "primeng/toolbar";
import {Router} from "@angular/router";
import {MessageService} from "primeng/api";
import {CountryStatistic, FrontendService, StatisticsService} from "../../../../generated/backend-api/thereabout";
import {CardModule} from "primeng/card";
import {TableModule} from "primeng/table";

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
    ToolbarModule,
    CardModule,
    TableModule
  ],
  templateUrl: './statistics.component.html',
  styleUrl: './statistics.component.scss'
})
export class StatisticsComponent implements OnInit {

  visitedCountries: Array<CountryStatistic> = [];

  constructor(private router: Router, private messageService: MessageService, private statisticsService: StatisticsService) {
  }

  ngOnInit(): void {
    this.statisticsService.getStatistics().subscribe(statistics => {
      this.visitedCountries = statistics.visitedCountries.sort((a, b) => b.numberOfDaysSpent - a.numberOfDaysSpent);
    });
  }

  navigateBackToMap() {
    this.router.navigate(['']);
  }

  getFlagEmoji(countryCode: string): string {
    const codePoints = countryCode
        .toUpperCase()
        .split('')
        .map((char: string) => 127397 + char.charCodeAt(0));
    return String.fromCodePoint(...codePoints);
  }

  countryNameFormat(countryStats: CountryStatistic): string {
    return `${this.getFlagEmoji(countryStats.countryIsoCode)} ${countryStats.countryName}`;
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
