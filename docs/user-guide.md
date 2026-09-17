# iDuoHome user guide

iDuoHome is an experimental Android launcher designed around a foldable phone, a four-column Home grid, and a fixed-app dock on the right. The cover shows one Home page at a time. Unfolding adds an editable workspace on the left: the first view pairs that workspace with Home 1, followed by Home 1 + Home 2, Home 2 + Home 3, and so on.

## Start and switch launchers

On a fresh install, **Welcome to iDuoHome** offers **Choose Home app**, **Add a widget**, **Explore Home**, and **Not now**. Choosing or skipping setup does not prevent later changes.

To make iDuoHome the launcher, choose **Set as home app** in customization, or open **Help & setup** and choose **Set iDuoHome as Home**. Android owns the final Home-app chooser. To switch away later, choose **Change home app**, or use Android **Settings → Apps → Default apps → Home app**. The exact Android path may vary by device.

## Move around Home

- Swipe horizontally across Home, the dock, or the right rail to move one page per gesture.
- Swipe right from Home 1 for Discover. Swipe left, press Back, or use its right-pointing arrow to return.
- Swipe past the last Home page for **All apps**. On an unfolded display, it occupies the right pane while the last Home page remains on the left, with the same half-page movement as Home. The cover display keeps the full-width list. Its **Search apps** field always searches installed apps locally. Korean app names are grouped by initial consonant (ㄱ, ㄴ, ㄷ, …), so 갤러리 and 게임 appear together under ㄱ.
- The dock and its search control stay on the right. The page controls also open Discover or All apps.
- The system status bar with notification icons always remains visible above Home and Discover.
- Pressing the system Home control from an app returns to the Home page or unfolded pair you last had visible. From All apps, search, or Discover it returns to the last Home view.

## Settings language

All launcher settings, setup screens, and related dialogs support English and Korean and follow the device language. On Android 13 or later, Android's app language settings can select English or Korean just for iDuoHome. Changing language preserves Home layout, icon choices, and widget bindings. Names supplied by other apps, icon packs, and widget providers keep their provider's language.

Translations live in `app/src/main/res/values/settings.xml` (English) and `app/src/main/res/values-ko/settings.xml` (Korean). Keep resource keys and format arguments aligned when adding settings.

## Customize Home

Long press empty space on Home to open **Add to Home**, then choose **Widgets**, **Wallpaper**, or **Customize launcher**. This includes the wallpaper above, below, and beside the grid, even when every cell is occupied. It works whether **Double-tap empty space to lock** is on or off. In **Make it yours** you can open:

- **Wallpaper & appearance** for launcher photos, Android wallpaper, and color mode.
- **Home layout** for icon size, row spacing, four to six fixed dock apps, Home apps, and widgets on the visible page.
- **Gestures & search** for Google Discover, app names, the upper-right status display, and Google search behavior. Turn off **Show Google Discover** to remove its page and button and stop its connection. The choice is saved between launches; turning it back on restores Discover.
- **Backup** to save or restore the layout.
- **Help & setup** for Home selection, widgets, shade gestures, and Discover.

After a layout edit, **Undo last layout change** appears in customization. It covers the latest supported layout change, so use it before making another edit.

## Apps, folders, and the dock

The unfolded Home panes reuse the grid width measured on the portrait cover screen, so app columns keep the cover spacing instead of being squeezed into a fixed-width pane. This measurement survives restarts. Before the first cover measurement, Home uses half the window as its reference; smaller multi-window layouts limit the width to keep the dock accessible. The shorter unfolded display keeps the configured icon size and row spacing, and scrolls vertically when the full grid does not fit.

Hold an app, then drag it to an empty cell, another page, or a vacant dock position. Neighboring Home icons move aside when possible. Pause at the left or right screen edge while holding to turn a page; dragging at the end can create another Home page.

Dragging between Home and the dock moves the shortcut instead of duplicating it. The dock shows only fixed apps, and **Home layout → Pinned app count** sets four, five, or six positions. Reducing the count removes the trailing dock shortcuts from Home placement while leaving the apps installed in **All apps**. When the dock is full, iDuoHome shows **Dock full • Move an app out first** and rejects a new arrival; it never evicts an app automatically. Existing dock apps can still be reordered. Drag a Home or dock shortcut to **Remove** to remove the shortcut without uninstalling the app.

Long press and release a Home app to open a small popup beneath it with **Select**, **Remove from Home**, and **Settings** (app info). The popup also offers widgets and folder creation. **Select** lets you select other items and remove their Home shortcuts together. A quick drop over another app keeps insertion/reordering; hold over its center until the folder marker appears, then release to create a folder.

In **All apps**, long press an app for **Add to Home** and **Uninstall**. The menu stays in the list until you move your finger. Keep holding and drag to switch to Home, then release over the desired cell; holding at an edge turns the page. Releasing outside a target or canceling returns to All apps without changing placement. **Add to Home** finds a free cell or shows the app's existing Home shortcut. **Uninstall** opens Android's confirmation screen for that app and profile; Android controls system-app and administrator restrictions.

If Android exposes a managed profile, **All apps** shows **Personal** and **Work** filters. A paused profile shows **Work apps are paused** and **Turn on work apps**. Availability and cross-profile widget access remain controlled by the profile administrator.

## Widgets

Open **Widgets** from an empty-space menu, **Add widget to this page** in customization, or **Add a widget** during setup. Search the catalog, select **Personal** or **Work** when those choices exist, then tap a preview to place it or hold it to drag. Android may ask you to allow the binding, and some providers open their own setup screen.

Hold an existing widget to pick it up, then drag it across cells or pages. A small amount of held finger jitter is allowed. Move into the lower-right **Remove** target to delete it from Home. Long press and release without dragging to show a popup beneath the widget with **Select**, **Remove from Home**, and **Settings**. Settings is enabled when the provider supports reconfiguration. The popup moves above the item if there is not enough room below.

Resize handles appear as soon as a widget is long-pressed and released. Drag an edge or the corner; the preview snaps to the four-column by seven-row grid and saves on release. Canceling a gesture or releasing an overlapping size keeps the previous layout. Provider size limits and fixed axes are respected. Moving onto another widget swaps their positions if both fit. Apps covered by a moved widget move into its vacated cells. Moves with insufficient space leave the layout unchanged. Tap outside or press Back to finish editing.

Scrollable Android widgets keep their native vertical scrolling when the touch begins on scrollable provider content. A horizontal swipe can still change Home pages. Hold still before moving when you intend to pick up the widget.

## Background and appearance

In **Wallpaper & appearance**, **Choose a photo** creates a private preview. It does not replace the current launcher background until you choose **Apply**; **Cancel** keeps the committed background. If selection is interrupted, choose **Resume** or **Cancel**. Recovery has been checked for activity recreation and a completed private preview file, but an interruption during the earlier decode step may require selecting the photo again.

The default background uses the bundled cover or main image to match the window width. It switches to the dark image from sunset until sunrise and to the light image during the day, independently of the app's appearance mode. Under **Default background**, choose **Use device location** or enter coordinates and select **Use this place**. Without a usable location, the background follows the system theme. **Use default background** removes a selected photo and restores these images.

**Choose wallpaper** opens Android's separate wallpaper picker. Select **Use system wallpaper** to show the Android wallpaper behind iDuoHome, including live wallpapers. Choosing an Android wallpaper does not replace the launcher's selected photo or default background until you switch to the system wallpaper.

Appearance choices are **Light**, **Dark**, **Follow system**, and **Sunrise / sunset**. Sunrise/sunset accepts coordinates through **Use this place**, or requests approximate location only when you choose **Use device location**. If location is unavailable, iDuoHome visibly falls back to the system theme. **Clear location** removes saved coordinates; iDuoHome does not request location in the background.

## Fold transition

Open **Wallpaper & appearance → Fold transition** and enable **Frosted-glass fold effect** (off by default). Home gains perspective and blur while folding/unfolding; on the inner display, the right pane stays sharp. **Effect strength** adjusts the appearance. These options persist locally and are not part of portable layout backups.

**Follow hinge angle when available** uses Android's standard hinge sensor after observing distinct intermediate angles. Readings limited to 0°, 90°, and 180° are treated as posture states; the launcher then plays a short timed effect when posture or cover/inner window changes. Turning angle following off always uses timed transitions. Precise tracking depends on firmware: the connected Fold8 (SM-F971N, Android 17) exposes a public hinge sensor but protects its separate Samsung Folding Angle sensor with `com.samsung.permission.SSENSOR`. Its continuous physical-angle behavior still needs a manual fold test.

The effect runs on visible Home only, pauses during editing and in multi-window, and follows Android's **Remove animations** setting. Android continues to decide when each screen turns on. System wallpaper surfaces, Discover, other apps, lock screen and system bars are outside the effect. No Shizuku, capture or overlay access is required. Android 13+ uses the perspective/frost renderer; Android 12 uses a simpler native blur.

## Optional shade gestures

On Home, swipe down from the left 70% to open Notifications or from the right 30% to open Quick Settings. The first attempt offers **Turn on shade gestures** because Android requires you to enable iDuoHome in Accessibility settings. This is optional and must be enabled by you; **Not now** leaves it off. The service only requests the system panel actions.

## Layout backup

Open **Backup**, choose **Save**, and select a document destination. Choose **Restore** to select a backup, inspect **Review restored layout**, then choose **Restore** again. **Cancel** leaves Home unchanged.

Backups contain Home and dock positions, folders, widget descriptions and spaces, layout presets, labels, search behavior, and status settings. They include the unfolded-only workspace. They do not include the selected background photo or live Android widget bindings. After restore, provider widgets keep their saved space but require **Reconnect**; unavailable apps leave empty positions, and work-profile entries may need manual placement. Review a backup before sharing because it can expose app names, folder names, and profile metadata.
