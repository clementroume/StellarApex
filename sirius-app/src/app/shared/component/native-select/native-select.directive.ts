import {Directive} from '@angular/core';

/**
 * Spartan look for a plain native <select> (keeps `[ngValue]` options and the native mobile picker;
 * `hlm-native-select` wraps the element, which breaks `ngValue`). Invalid state from aria-invalid.
 */
@Directive({
  selector: 'select[appNativeSelect]',
  standalone: true,
  host: {
    class: 'h-11 md:h-10 w-full min-w-0 rounded-md border border-input bg-transparent dark:bg-input/30 px-2.5 text-base ' +
      'md:text-sm shadow-xs outline-none transition-[color,box-shadow] focus-visible:border-ring focus-visible:ring-3 ' +
      'focus-visible:ring-ring/50 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20 ' +
      'disabled:cursor-not-allowed disabled:opacity-50 [&_option]:bg-popover [&_option]:text-popover-foreground'
  }
})
export class NativeSelectDirective {
}
