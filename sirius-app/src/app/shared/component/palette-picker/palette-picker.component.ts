import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';
import {ThemeService} from '../../../core/services/theme.service';
import {PALETTES} from '../../../api/antares/models/user.model';

/**
 * Colour palette choice as a native radio group (arrow keys, screen readers), each option previewed
 * in its own colours for the current light/dark theme.
 */
@Component({
  selector: 'app-palette-picker',
  standalone: true,
  imports: [TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fieldset class="grid grid-cols-2 sm:grid-cols-4 gap-3">
      <legend class="sr-only">{{ 'SETTINGS.PALETTE_LABEL' | translate }}</legend>
      @for (p of palettes; track p) {
        <label class="flex flex-col gap-2 cursor-pointer rounded-lg p-2 border border-transparent
                      has-checked:border-(--color-primary) has-focus-visible:ring-2 has-focus-visible:ring-(--color-primary)">
          <input type="radio" name="palette" class="sr-only" [value]="p"
                 [checked]="themeService.currentPalette() === p" (change)="themeService.setPalette(p)"/>
          <span aria-hidden="true" [attr.data-palette]="p" [attr.data-theme]="themeService.currentTheme()"
                class="flex flex-col gap-1.5 rounded-md p-2 bg-(--sa-base-200) border border-(--sa-base-300)">
            <span class="flex flex-col gap-1 rounded p-1.5 bg-(--sa-base-100)">
              <span class="h-1.5 w-3/4 rounded-full bg-(--sa-base-content) opacity-70"></span>
              <span class="h-3 rounded bg-(--sa-primary)"></span>
            </span>
            <span class="flex gap-1">
              <span class="h-2 flex-1 rounded-full bg-(--sa-secondary)"></span>
              <span class="h-2 flex-1 rounded-full bg-(--sa-accent)"></span>
            </span>
          </span>
          <span class="flex flex-col">
            <span class="text-sm font-medium">{{ 'SETTINGS.PALETTES.' + p + '.NAME' | translate }}</span>
            <span class="text-xs opacity-70">{{ 'SETTINGS.PALETTES.' + p + '.DESC' | translate }}</span>
          </span>
        </label>
      }
    </fieldset>
  `
})
export class PalettePickerComponent {
  // --- INJECTIONS ---
  protected readonly themeService = inject(ThemeService);

  // --- STATE (SIGNALS & VARIABLES) ---
  protected readonly palettes = PALETTES;
}
