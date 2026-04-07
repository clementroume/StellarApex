import {Component, Input} from '@angular/core';
import {RouterLink} from '@angular/router';
import {NgIconComponent} from '@ng-icons/core';
import {NgOptimizedImage, UpperCasePipe} from '@angular/common';

@Component({
  selector: 'app-list-item',
  standalone: true,
  imports: [RouterLink, NgIconComponent, UpperCasePipe, NgOptimizedImage],
  template: `
    <li>
      <a [routerLink]="link"
         class="w-full text-left flex items-center justify-between p-4 hover:bg-base-200/50 cursor-pointer transition-colors group">

        <div class="flex items-center gap-4">

          <div class="avatar placeholder">
            <div
              class="mask mask-squircle w-12 h-12 bg-base-200 text-base-content/50 flex items-center justify-center overflow-hidden">
              @if (imageUrl) {
                <img [ngSrc]="imageUrl"
                     [alt]="title"
                     width="48"
                     height="48"
                     class="object-cover w-full h-full"/>
              } @else {
                <span class="text-xl font-bold">{{ title.charAt(0) | uppercase }}</span>
              }
            </div>
          </div>

          <div class="flex flex-col">
            <div class="font-semibold text-lg flex items-center gap-2">
              {{ title }}

              @if (badge) {
                <span class="badge badge-ghost badge-sm font-normal">{{ badge }}</span>
              }
            </div>

            @if (subtitle) {
              <span class="text-sm opacity-60">{{ subtitle }}</span>
            }
          </div>

        </div>

        <div class="text-neutral group-hover:text-primary transition-colors pr-2">
          <ng-icon name="heroChevronRight" size="1rem"></ng-icon>
        </div>

      </a>
    </li>
  `
})
export class ListItemComponent {
  @Input({required: true}) title!: string;
  @Input({required: true}) link!: any[];
  @Input() imageUrl?: string;
  @Input() badge?: string;
  @Input() subtitle?: string;
}
