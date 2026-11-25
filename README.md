# blackview-autostart

The DokeOS autostart allowlist as a library instead of a shell script plus a
one-off app.

On a Blackview/DokeOS phone the autostart list is the gate that decides whether
a package runs after boot. Without an entry, the package is `stopped=true` and
`BVPFWLogger: AppAutoStartupPolicy` blocks its receiver; with one,
`LOCKED_BOOT_COMPLETED` arrives at about 27 s. The list is guarded by the
signature permission `com.freeme.freemanager.permission.ACCESS_INTERFACE`, so
neither adb nor an ordinary app can write it. Both routes here can only drive
the System Manager screen:

- the **library** walks the screen through an accessibility service;
- the **CLI** drives the same screen over adb with `uiautomator`.

The user still enables the accessibility service and still grants the row once.
This project automates the screen, not the vendor's signature.

## Modules

```
:lib     io.github.mbanucu:blackview-autostart   AAR, minSdk 29
:sample  a demo app the toggle drives
cli/     adb + uiautomator, no app installed
```

## Use the library

```java
if (!BlackviewAutostart.isSupported(context)) {
    // not DokeOS; stop with a named reason
}
if (!BlackviewAutostart.isAccessibilityEnabled(context)) {
    BlackviewAutostart.openAccessibilitySettings(context); // user enables it
}
BlackviewAutostart.requestToggle(context, "Phone link proxy"); // off and back on
BlackviewAutostart.requestAllowed(context, "Phone link proxy", true);
BlackviewAutostart.openPanel(context);
```

The AAR's manifest contributes the accessibility service, the in-app command
receiver and the boot recorder, so a consumer app declares none of them. The
boot record answers the question the allowlist exists for:

```java
BootStart.Record boot = BootStart.lastBoot(context);
// boot.seen, boot.delayMs, boot.beforeUnlock, boot.signals
```

`requestToggle` is the re-registration step: DokeOS wants the row taken through
off and back on, because a reinstall silently invalidates an entry while the row
still reads on. `requestAllowed` leaves the row in one state.

The calls return `false` instead of queueing work when the phone is not DokeOS
or the service is not enabled. Nothing in the library enables the service, and
nothing writes the vendor allowlist.

## CLI

```sh
export PHONE_SERIAL=...        # optional
cli/autostart-allow.sh --list
cli/autostart-allow.sh --check "Phone link proxy"
cli/autostart-allow.sh "Phone link proxy"       # allow it
cli/autostart-allow.sh --off "Phone link proxy"
```

Exit codes: `0` allowed/already/check found it; `1` not found or still off;
`2` usage; `3` the list could not be read (locked phone, or not the ROM this
expects). The phone must be unlocked.

## Build

```sh
export ANDROID_HOME="$HOME/android-sdk"
./gradlew :lib:assembleRelease :sample:assembleDebug
./gradlew :lib:publishToMavenLocal          # io.github.mbanucu:blackview-autostart:0.1.0
```

On Termux, point AGP at the bionic aapt2; the SDK's is glibc:

```sh
./gradlew -Pandroid.aapt2FromMavenOverride="$PREFIX/bin/aapt2" \
    :lib:assembleRelease :sample:assembleDebug
```

Versions and SDK levels live in `gradle/libs.versions.toml`. AGP 9.5.2 and the
Gradle 9.9.2 wrapper are pinned as a pair.

## Acceptance still open

- `publishToMavenLocal` produces the AAR, sources and javadoc; no Maven Central
  release exists yet.
- The sample toggle has not been run against the XPLORE 2 in this checkout.
  Device tests for allowlist off (blocked) and on (toggled) come before a
  release, as does the CI publish workflow from wireless-adb-keeper#94.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
