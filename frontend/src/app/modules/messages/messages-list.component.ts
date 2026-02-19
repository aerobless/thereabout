import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ToolbarComponent } from '../../shared/toolbar/toolbar.component';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { InputTextModule } from 'primeng/inputtext';
import { TooltipModule } from 'primeng/tooltip';
import { CardModule } from 'primeng/card';
import { SelectModule } from 'primeng/select';
import type { FilterMetadata } from 'primeng/api';
import { Message, MessageService as MessageApiService } from '../../../../generated/backend-api/thereabout';

interface FilterMeta {
  value?: string | Date | Date[] | null;
  matchMode?: string;
}

@Component({
  selector: 'app-messages-list',
  standalone: true,
  imports: [
    RouterModule,
    FormsModule,
    ToolbarComponent,
    TableModule,
    InputTextModule,
    TooltipModule,
    CardModule,
    SelectModule,
  ],
  templateUrl: './messages-list.component.html',
  styleUrl: './messages-list.component.scss',
})
export class MessagesListComponent {
  messages: Message[] = [];
  totalRecords = 0;
  loading = false;
  defaultSortField = 'timestamp';
  defaultSortOrder = -1; // desc
  rows = 20;

  sourceOptions = [{ label: 'WhatsApp', value: 'WhatsApp' }];
  /** Bound to p-table [filters]; required for PrimeNG 21 column filter UI */
  tableFilters: { [s: string]: FilterMetadata | FilterMetadata[] } = {};

  private first = 0;
  private sortField = this.defaultSortField;
  private sortOrder = this.defaultSortOrder;

  constructor(private messageApiService: MessageApiService) {}

  loadMessages(event: TableLazyLoadEvent): void {
    this.first = event.first ?? 0;
    const size = event.rows ?? this.rows;
    this.sortField = (event.sortField as string) ?? this.defaultSortField;
    this.sortOrder = event.sortOrder ?? this.defaultSortOrder;
    const sort = `${this.sortField},${this.sortOrder === 1 ? 'asc' : 'desc'}`;
    const messageFilter = this.extractTextFilter(event.filters?.['message'] as FilterMeta | FilterMeta[] | undefined);
    const globalValue = (event.filters?.['global'] as FilterMeta)?.value;
    const globalSearch = typeof globalValue === 'string' ? globalValue.trim() : undefined;
    const search = messageFilter ?? (globalSearch || undefined);

    const tsFilter = event.filters?.['timestamp'] as FilterMeta | FilterMeta[] | undefined;
    const dateFrom = this.extractDateFromFilter(tsFilter);
    const dateTo = this.extractDateToFilter(tsFilter);
    const source = this.extractTextFilter(event.filters?.['source'] as FilterMeta | FilterMeta[] | undefined);
    const sender = this.extractTextFilter(event.filters?.['sender'] as FilterMeta | FilterMeta[] | undefined);
    const receiver = this.extractTextFilter(event.filters?.['receiver'] as FilterMeta | FilterMeta[] | undefined);

    this.fetchPage(Math.floor(this.first / size), size, sort, search, dateFrom, dateTo, source ?? undefined, sender, receiver);
  }

  private extractDateFromFilter(f: FilterMeta | FilterMeta[] | undefined): string | undefined {
    const single = Array.isArray(f) ? f[0] : f;
    const v = single?.value;
    if (v == null) return undefined;
    if (Array.isArray(v) && v[0]) return this.toIsoDate(v[0]);
    if (v instanceof Date) return this.toIsoDate(v);
    return undefined;
  }

  private extractTextFilter(f: FilterMeta | FilterMeta[] | undefined): string | undefined {
    const single = Array.isArray(f) ? f[0] : f;
    const v = single?.value;
    if (typeof v !== 'string') return undefined;
    const trimmed = v.trim();
    return trimmed.length > 0 ? trimmed : undefined;
  }

  private extractDateToFilter(f: FilterMeta | FilterMeta[] | undefined): string | undefined {
    const single = Array.isArray(f) ? f[0] : f;
    const v = single?.value;
    if (v == null) return undefined;
    if (Array.isArray(v) && v[1]) return this.toIsoDate(v[1]);
    if (Array.isArray(v) && v[0]) return this.toIsoDate(v[0]);
    if (v instanceof Date) return this.toIsoDate(v);
    return undefined;
  }

  private toIsoDate(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  private fetchPage(
    page: number,
    size: number,
    sort: string,
    search: string | undefined,
    dateFrom?: string,
    dateTo?: string,
    source?: string,
    sender?: string,
    receiver?: string
  ): void {
    this.loading = true;
    this.messageApiService.getMessageList(page, size, sort, search, dateFrom, dateTo, source, sender, receiver).subscribe({
      next: (pageResponse) => {
        this.messages = pageResponse.content ?? [];
        this.totalRecords = pageResponse.totalElements ?? 0;
        this.loading = false;
      },
      error: () => {
        this.messages = [];
        this.totalRecords = 0;
        this.loading = false;
      },
    });
  }

  formatMessageTime(timestamp: string | undefined): string {
    if (!timestamp) return '--';
    return new Date(timestamp).toLocaleTimeString('en-US', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
    });
  }

  /** ISO date format 2025-12-31 */
  formatMessageDate(timestamp: string | undefined): string {
    if (!timestamp) return '--';
    const d = new Date(timestamp);
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }
}
