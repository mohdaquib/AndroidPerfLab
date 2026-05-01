# Benchmark Methodology

This document covers how benchmarks in this project are designed, what hardware conditions are
required for trustworthy results, why the build configuration is the way it is, how to read the
output metrics, and what the numbers cannot tell you.

---

## Device specification

### CI environment

CI runs macrobenchmarks on a GitHub-hosted runner using the
[`reactivecircus/android-emulator-runner`](https://github.com/ReactiveCircus/android-emulator-runner)
action:

| Property | Value |
|---|---|
| API level | 34 (Android 14) |
| Architecture | x86_64 |
| Target | default (AOSP, no Play Services) |
| Boot timeout | 600 s |
| Compilation mode | `CompilationMode.None()` — JIT only, no AOT |

Emulator results are inherently noisier than physical hardware (see [Limitations](#limitations)).
The emulator configuration intentionally suppresses the two errors the benchmark runner would
otherwise emit:

```kotlin
// benchmarks/build.gradle.kts
testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] =
    "EMULATOR,DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
```

`EMULATOR` silences the "running on emulator" error. `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`
silences a permissions-check false positive that appears on API 34 emulators. Neither suppression
affects what is actually measured.

### Physical device setup

Running on physical hardware reduces variance significantly. Before measuring, lock the CPU and
GPU clocks so the SoC cannot throttle or boost mid-run.

**Prerequisites:** the device must be rooted or running a userdebug/eng build. Stock consumer
devices cannot lock clocks.

```bash
# 1. Connect the device and verify adb access
adb devices

# 2. Lock clocks using the AndroidX Benchmark Gradle task
#    (available when the benchmark module uses MacrobenchmarkRule)
./gradlew :benchmarks:lockClocks

# 3. Run the benchmarks
./gradlew :benchmarks:connectedBenchmarkAndroidTest

# 4. Unlock clocks when done (skipping this degrades battery life)
./gradlew :benchmarks:unlockClocks
```

`lockClocks` pins CPU frequency to a fixed mid-range value (not max), disables the interactive
governor, and locks the GPU where the kernel exposes a control node. The fixed frequency is
intentionally below peak so thermal headroom is preserved across a full benchmark run.

**Recommended device properties for reproducible results:**

- Disable Wi-Fi and mobile data (reduces background wakeups).
- Charge to ≥ 80 % or keep plugged in (battery saver policies alter scheduling at low charge).
- Turn off all notification delivery from other apps (`adb shell settings put global
  zen_mode 1`).
- Keep display on (`adb shell svc power stayon true`) — some devices throttle when the
  screen is off.

---

## Why nonDebuggable builds are required

All macrobenchmarks in this project run against the `benchmark` build type, defined in
`app/build.gradle.kts`:

```kotlin
create("benchmark") {
    initWith(getByName("release"))   // inherits minification + R8
    signingConfig = signingConfigs.getByName("debug")  // debug cert for CI
    isDebuggable = false
}
```

`isDebuggable = false` is not optional. Debug builds carry several sources of overhead that
inflate every metric and make before/after comparisons unreliable:

| Overhead source | Effect on benchmarks |
|---|---|
| JDWP agent always attached | Adds ~5–15 ms to every cold start; unpredictable per-frame cost |
| JIT profiling hooks | Extra bookkeeping per method call; suppresses some JIT optimisations |
| `StrictMode` and debug assertions | Extra allocations and thread checks on every UI operation |
| Compose `isDebugInspectorInfoEnabled` | Turns on slot-table inspection for Layout Inspector; adds recomposition overhead |
| R8 / ProGuard disabled | Dead code not stripped; more class loading; larger DEX → slower first-frame JIT |

The benchmark runner enforces this: if `isDebuggable = true`, it emits a `DEBUG_BUILD` error and
refuses to record results (unless you add `"DEBUG_BUILD"` to `suppressErrors`, which would
invalidate the data).

The `benchmark` build type keeps debug signing so the APK can be installed on CI without a
release keystore. The signing cert has no effect on runtime performance.

---

## How to interpret frame timing metrics

`ScrollBenchmark` uses `FrameTimingMetric`, which records a distribution of frame durations over
5 iterations of 5 down-scrolls + 5 up-scrolls. The output JSON contains these fields per
benchmark:

```
frameDurationCpuMs.p50   — median frame duration (CPU time only)
frameDurationCpuMs.p90   — 90th percentile
frameDurationCpuMs.p95   — 95th percentile
frameDurationCpuMs.p99   — 99th percentile
frameOverrunMs           — signed wall-clock budget overrun (hardware timestamp devices only)
jankyFrameCount          — frames that exceeded the 16.67 ms / 60 fps deadline
jankyFramePercent        — janky frames as a share of total frames rendered
```

### Reading the percentiles

Think of the percentile distribution as a story about different kinds of rendering problems:

**p50** reflects steady-state cost — what a typical frame costs when nothing unusual is happening.
A high p50 (> 8 ms on a 60 Hz display) means the per-frame work budget is already half-consumed
before any hiccup occurs. The optimised scroll screen targets p50 around 4–6 ms.

**p90** reflects how well the app handles light variation — minor GC pauses, occasional longer
layout passes, background service wakeups. A p90 below 10 ms means nine out of ten frames are
comfortable even under normal system noise.

**p99** is the headline regression gate in this project. It captures the worst 1 % of frames —
the frames a user would perceive as a visible stutter. The CI threshold is **16.0 ms**:

```python
# benchmarks/BenchmarkResultsParser.py
FRAME_P99_THRESHOLD_MS = 16.0
```

This is intentionally 1 % tighter than the 16.67 ms budget for 60 fps. The reasoning: if p99 is
already at the deadline, a single additional GC pause or thermal event pushes real-world p99
over the cliff. A p99 of 16 ms leaves almost no headroom.

The threshold is only enforced for `scrollAnimatedList_optimized`. The unoptimized variant is
allowed to exceed it — its purpose is to confirm the baseline is genuinely slow, not to pass CI.

**p95** is not gated but is worth watching: a large gap between p90 and p95 typically signals
infrequent but expensive allocations (bitmaps, large `List` copies) rather than per-frame waste.

### `frameOverrunMs` vs `frameDurationCpuMs`

`frameDurationCpuMs` measures only CPU-side work (including RenderThread). It is available on
all devices. `frameOverrunMs` measures wall-clock overrun relative to the frame deadline and
requires hardware GPU-timestamp support (most Pixel devices, some Snapdragons). On the CI
emulator, `frameOverrunMs` is absent from the JSON; do not treat its absence as a failure.

### `jankyFrameCount` vs p99

These are complementary, not redundant. p99 tells you how bad the worst frames are.
`jankyFrameCount` tells you how many frames crossed the 16.67 ms deadline. A test can have a
low p99 but a non-zero jank count if a handful of frames spiked just barely over the deadline.
For 60 Hz content, a jank count of zero is the target; one or two janky frames per 100 is
acceptable on non-rooted emulator hardware.

---

## Startup timing metrics

`StartupBenchmark` and `AppStartupBenchmark` use `StartupTimingMetric` across 10 iterations:

```
timeToInitialDisplayMs  — TTID: system-measured time from process start to first frame drawn
timeToFullDisplayMs     — TTFD: time until the app calls reportFullyDrawn()
```

**TTID** is reported by the system and cannot be manipulated by the app. It ends when the window
surface receives its first rendered frame — even if that frame shows only a blank background.

**TTFD** is the app-reported milestone. `MainActivity` calls `reportFullyDrawn()` after the
Compose layout pass completes and the feed `LazyColumn` is scrollable. TTFD is absent for
`StartupMode.HOT` because `onCreate()` is not called in that mode and `reportFullyDrawn()` is
never invoked.

The CI cold-start threshold is **800 ms TTID**:

```python
COLD_START_THRESHOLD_MS = 800
```

The optimised build targets 150–350 ms; the 800 ms gate is a wide safety margin designed to catch
regressions (e.g. an SDK accidentally moved back onto the main thread) rather than to certify
production quality.

The startup tests use `CompilationMode.None()` (JIT only, no AOT pre-compilation). This produces
the worst-case startup time — the same condition a user experiences on first install before ART
has had time to profile and compile. Baseline Profiles are generated separately via
`./gradlew :app:generateBaselineProfile` and are measured independently.

---

## Limitations and variance expectations

### Emulator variance

CPU clock locking is not possible on the emulator. The emulator shares host CPU cores with other
processes and is subject to the host scheduler. Expect ±30–50 ms variance on startup metrics
and ±2–4 ms variance on p99 frame duration across runs. This is why:

- Startup uses 10 iterations (more samples reduce the impact of outliers).
- Scroll uses 5 iterations (frame metrics are per-frame averages over hundreds of frames, so
  fewer iterations are needed for stable statistics).
- The CI threshold for cold start (800 ms) is set 3× above the measured optimised value
  (~250 ms) to absorb emulator noise.

### `CompilationMode.None()` and JIT behaviour

All benchmarks in this project run with `CompilationMode.None()`. JIT compilation happens during
the benchmark run, which means the first iteration is always slower (the JIT is profiling) and
later iterations are faster (hot methods are compiled). The benchmark library accounts for this
by recording all iterations but reporting the distribution — look at p50 and p90 across multiple
runs rather than a single median.

If you switch to `CompilationMode.Full()` (AOT), numbers will be lower and more consistent but
will not represent install-fresh behaviour. `CompilationMode.None()` is the right choice for
detecting regressions in production conditions.

### Thermal throttling on physical devices

Even with locked clocks, sustained benchmarks on physical hardware can trigger thermal
throttling if the device approaches its temperature limit. Signs of throttling:

- Startup times that increase monotonically across iterations (not random noise).
- Frame p99 that is higher for `scrollAnimatedList_optimized` than for `scrollAnimatedList_unoptimized`
  (impossible without throttling — the unoptimized path does more work).

If you observe these patterns, let the device cool for 5–10 minutes and re-run. Plugging in
USB-C power delivery can worsen thermals on some devices; consider unplugging during the run.

### What the numbers do and do not represent

| The numbers DO reflect | The numbers DO NOT reflect |
|---|---|
| Regression introduced in the code under test | Absolute production performance on a user's device |
| Relative improvement from a specific optimisation | Performance under network I/O or database load |
| Worst-case startup before ART profiling | Performance after a user's device has profiled and compiled the app |
| Per-frame Compose rendering cost | GPU-bound rendering (these benchmarks are CPU-bound) |
| Recomposition pass count (unit test metric) | Number of composables recomposed within a single pass |

Recomposition counts in `RecompositionBenchmark` measure `Recomposer.changeCount` — the number
of complete composition passes applied, not the number of individual composables that re-ran.
One click that triggers one state change = one pass = `delta` of 1 in the optimised build.
The assertion `assertEquals(1L, delta)` verifies no cascading second pass was triggered; it
does not verify which composables were skipped within that pass. Use Layout Inspector's
recomposition highlighting to inspect per-composable skip behaviour.
