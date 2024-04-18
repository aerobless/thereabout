import { Component } from '@angular/core';
import {GoogleMap} from "@angular/google-maps";

@Component({
  selector: 'app-locationhistory',
  standalone: true,
  imports: [
    GoogleMap
  ],
  templateUrl: './locationhistory.component.html',
  styleUrl: './locationhistory.component.scss'
})
export class LocationhistoryComponent {

}
