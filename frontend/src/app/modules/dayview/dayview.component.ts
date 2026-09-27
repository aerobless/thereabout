import { Component, OnInit, ChangeDetectionStrategy, DestroyRef, inject, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, ActivatedRoute } from '@angular/router';
import { DatePipe } from '@angular/common';
import { DatePicker, DatePickerModule } from 'primeng/datepicker';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { CardModule } from 'primeng/card';
import { CalendarCardComponent } from '../calendar/calendar-card.component';
import { EnergyCardComponent } from './energy/energy-card.component';
import { DurationCardComponent } from './duration/duration-card.component';
import { ChoicesCardComponent } from './choices/choices-card.component';
import { WeightCardComponent } from './weight/weight-card.component';
import { HeartRateCardComponent } from './heart/heart-rate-card.component';
import { HrvCardComponent } from './heart/hrv-card.component';
import { DayLocationComponent } from './day-location.component';
import { DayStepsComponent } from './day-steps.component';
import { DayMessagesComponent } from './day-messages.component';
import { DayWorkoutsComponent } from './day-workouts.component';
import { DayViewData } from './day-view-data';
import { DayLocationState } from './day-location-state';
import { DayHealthData } from './day-health-data';
import { DayMessageData } from './day-message-data';
import { localDateString, parseLocalDate } from '../../shared/dates/local-date';
@Component({
  selector: 'app-dayview',
  imports: [DatePipe, DatePickerModule, FormsModule, ButtonModule, TooltipModule, CardModule, CalendarCardComponent, EnergyCardComponent, DurationCardComponent, ChoicesCardComponent, WeightCardComponent, HeartRateCardComponent, HrvCardComponent, DayLocationComponent, DayStepsComponent, DayMessagesComponent, DayWorkoutsComponent],
  providers: [DayViewData, DayLocationState, DayHealthData, DayMessageData],
  templateUrl: './dayview.component.html', styleUrl: './dayview.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DayviewComponent implements OnInit {
  readonly day = inject(DayViewData);
  readonly location = inject(DayLocationState);
  readonly health = inject(DayHealthData);
  readonly messages = inject(DayMessageData);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  get selectedDate(): Date { return this.day.selectedDate(); }
  set selectedDate(date: Date) { this.day.selectedDate.set(date); }
  get mobileLocationView(): boolean { return this.location.mobileLocationView(); }
  get isSelectedDayToday(): boolean { return localDateString(this.selectedDate) === localDateString(new Date()); }
  readonly dateToString = localDateString;
  constructor() {
    const mobile = window.matchMedia?.('(max-width: 768px)');
    const onViewportChange = (event: MediaQueryListEvent) => {
      const picker = this.datePicker(); if (picker?.overlayVisible()) picker.hideOverlay();
      this.location.mobileLocationView.set(event.matches);
      this.location.highlightedLocationEntry.set(undefined);
      if (event.matches) this.location.locationEditDraft.set(null);
    };
    mobile?.addEventListener('change', onViewportChange);
    this.destroyRef.onDestroy(() => mobile?.removeEventListener('change', onViewportChange));
  }
  get selectedDaySuffix(): string {
    const day = this.selectedDate?.getDate();
    if (!day) return '';
    if (day >= 11 && day <= 13) return 'th';
    switch (day % 10) {
      case 1: return 'st';
      case 2: return 'nd';
      case 3: return 'rd';
      default: return 'th';
    }
  }

  readonly datePicker = viewChild<DatePicker>('dayDatePicker');

  private hasLoadedInitialData = false;

  goToPreviousDay() {
    const previousDay = new Date(this.selectedDate);
    previousDay.setDate(previousDay.getDate() - 1);
    this.setDateAndUpdateUrl(previousDay);
  }

  goToNextDay() {
    const nextDay = new Date(this.selectedDate);
    nextDay.setDate(nextDay.getDate() + 1);
    this.setDateAndUpdateUrl(nextDay);
  }

  goToToday() {
    const today = new Date();
    this.setDateAndUpdateUrl(today);
  }

  onDateChange() {
    this.updateUrl();
    this.loadAllData();
  }

  setDateAndUpdateUrl(date: Date) {
    if (!date || !Number.isFinite(date.getTime())) return;
    this.selectedDate = new Date(date);
    this.updateUrl();
    this.loadAllData();
  }

  private updateUrl(skipHistory = false) {
    const dateStr = this.dateToString(this.selectedDate);
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { date: this.isSelectedDayToday ? null : dateStr },
      queryParamsHandling: 'merge',
      replaceUrl: skipHistory
    });
  }

  ngOnInit() {
    // Read date from URL query params, default to today if not provided
    this.route.queryParams.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      const dateParam = params['date'];
      if (dateParam) {
        const parsedDate = parseLocalDate(dateParam) ?? new Date(NaN);
        if (!isNaN(parsedDate.getTime())) {
          // Only update if the date actually changed (to avoid reloading when we update URL ourselves)
          const newDateStr = this.dateToString(parsedDate);
          const currentDateStr = this.dateToString(this.selectedDate);
          if (!this.hasLoadedInitialData || newDateStr !== currentDateStr) {
            this.selectedDate = parsedDate;
            this.loadAllData();
          }
          this.hasLoadedInitialData = true;
          if (this.isSelectedDayToday) this.updateUrl(true);
        }
      } else {
        // A bare URL always means today, including browser Back/Forward navigation.
        const today = new Date();
        if (!this.hasLoadedInitialData || this.dateToString(this.selectedDate) !== this.dateToString(today)) {
          this.selectedDate = today;
          this.loadAllData();
        }
        this.hasLoadedInitialData = true;
      }
    });
  }

  private loadAllData() {
    this.location.loadDayViewData();
    this.health.loadHealthData();
    this.messages.loadMessages();
  }

  get dayLinks() {
    const date = this.dateToString(this.selectedDate);
    return [
      { label: 'Photos', icon: 'pi-image', url: `https://photos.google.com/search/${date}` },
      { label: 'Expenses', icon: 'pi-wallet', url: `https://firefly.w1nter.com/transactions/all/${date}/${date}` }
    ];
  }
}