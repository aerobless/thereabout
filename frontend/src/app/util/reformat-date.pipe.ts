import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'reformatDate',
  standalone: true
})
export class ReformatDatePipe implements PipeTransform {

  transform(value: string): string {
    if (!value) return value;

    // Assuming the input format is always YYYY-MM-DD
    const dateParts = value.split('-');
    if (dateParts.length !== 3) return value;

    const year = dateParts[0];
    const month = dateParts[1];
    const day = dateParts[2];

    return `${day}.${month}.${year}`;
  }
}
