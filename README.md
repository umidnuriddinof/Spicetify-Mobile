# Spicetify Mobile

Spicetify Mobile is an offline Android companion for organizing and previewing
Spicetify-style color themes. It does not modify Spotify, block ads, use
Accessibility services, or execute code from theme files.

## MVP

- Browse the built-in and imported theme library, search by name/author, and
  filter by source.
- Preview a theme's background, surface, accent, and text colors in the app.
- Import and export a versioned JSON theme package using Android's system file
  picker.
- Keep the library on-device and available offline.

Theme packages contain only text metadata and hexadecimal color values. Package
format version 1:

```json
{
  "format": "spicetify-mobile-theme-pack",
  "version": 1,
  "themes": [
    {
      "id": "midnight",
      "name": "Midnight",
      "author": "Example",
      "description": "A calm dark palette.",
      "background": "#10131A",
      "surface": "#1B202B",
      "accent": "#8B7CFF",
      "text": "#F4F5FA"
    }
  ]
}
```

The app validates package versions, limits imported package size and theme
count, and accepts only `#RRGGBB` or `#AARRGGBB` colors.

## Build

Open this project in Android Studio and let it sync the Gradle project, then
run the `app` configuration on an emulator or device. The app requires Android
6.0 (API 23) or newer.
