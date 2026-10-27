# Code overview

This guide describes the purpose of each Java class in `src` and how the
program works as a whole. The project is a hardware-information reader and
example report generator; it reads information from the operating system
rather than asking the user to enter hardware details.

## Program flow

1. `demo` checks whether a report filename was provided.
2. On Linux, it samples CPU counters about one second apart to calculate
   overall and per-core load, and reads memory information. It then gathers
   PCI and USB devices through their respective reader classes.
3. `idLookup` translates numeric vendor, device, and PCI class IDs into names.
4. The demo sorts and summarizes the PCI devices, lists USB devices, and
   prints the report. With `--report <file>`, it writes the same report to a
   UTF-8 text file instead.

Run the program from the project folder containing `ids`, because the ID
databases are loaded relative to the current working directory. Build and run
instructions are in [RUNNING.md](RUNNING.md).

## Classes

### `demo`

The example application's entry point (`main`). It gathers information from
the reader classes, formats the hardware inventory, and either prints it to
the terminal or saves it to a report file.

- No arguments: print the report to the terminal.
- `--report <file>`: write the report to the given path. Parent directories
  are created when needed, and an existing file is overwritten.
- `--vendor <name>`: keep devices whose vendor name or ID contains the value
  (case-insensitive).
- `--class <name-or-code>`: keep PCI devices whose class name contains the
  value or whose hexadecimal class code starts with it.
- `--bus <number>`: keep PCI and USB devices on the specified decimal bus,
  when the operating system reports a bus number.
- `--type all|pci|usb`: select which device list to display. CPU and memory
  information remains included when available.
- `--gui`: open the optional Swing interface. This option is used by itself;
  omit it to use the command-line interface.
- Any other argument form results in a usage error.

The report includes sampled CPU load and memory details when running on Linux,
PCI devices grouped into network, display, storage, bridge, and USB-controller categories,
remaining devices grouped by PCI class, totals per class and vendor, and USB
devices sorted by vendor. Devices within each PCI group are sorted by vendor.
Unknown IDs are shown as hexadecimal values or `unknown`. Multiple filters can
be combined; a class filter applies only to PCI and omits the USB list.

The GUI gathers one hardware snapshot at startup, displays it in CPU, memory,
PCI, and USB tabs, and provides per-tab text filters and sortable columns. Its
export button writes the currently visible rows from all tabs to a UTF-8 text
file.

### `cpuInfo`

Reads processor data from Linux `/proc/cpuinfo` and `/proc/stat`.

- `getCount()` counts logical processors.
- `getModel()` returns the processor model name where available.
- `getField(name)` returns the first matching named field from `/proc/cpuinfo`.
- `getBusy(core)` and `getIdle(core)` return cumulative CPU ticks for a core.

The tick values are not percentages. To calculate usage, read busy and idle
twice with a delay between samples, then calculate:

```text
busy difference / (busy difference + idle difference) * 100
```

### `memInfo`

Reads memory values from Linux `/proc/meminfo`. All returned sizes are in KiB.

- `getTotal()` returns installed memory.
- `getAvailable()` returns memory Linux reports as available for use.
- `getUsed()` returns total minus available.
- `getUsedPercentage()` returns used memory as a percentage of total.
- `field(name)` reads another named value, such as `SwapTotal`.

If a value cannot be read, these methods return `-1`.

### `pciInfo`

Finds PCI devices and provides their addresses, IDs, and class codes. On Linux,
it reads `/sys/bus/pci/devices`; on Windows it queries Plug and Play device
information; on macOS it reads `system_profiler` output.

- `getCount()` returns the number of discovered devices.
- `getAddress(i)` returns the address or operating-system identifier.
- `getBus(i)`, `getDevice(i)`, and `getFunction(i)` return location numbers
  when available; otherwise they return `-1`.
- `getVendorId(i)`, `getDeviceId(i)`, and `getClassCode(i)` return numeric IDs.
- `rescan()` discards the cached list so the next call reads devices again.

The index `i` is zero-based and must be between `0` and `getCount() - 1`.

### `usbInfo`

Finds USB devices and provides IDs and descriptive fields. On Linux, it reads
`/sys/bus/usb/devices`; on Windows it queries Plug and Play; on macOS it reads
`system_profiler` output.

- `getCount()` returns the number of discovered devices.
- `getName(i)`, `getManufacturer(i)`, and `getProduct(i)` return text reported
  by the device or operating system.
- `getVendorId(i)` and `getProductId(i)` return numeric IDs.
- `getBusNumber(i)` and `getDeviceNumber(i)` return Linux-style numbers when
  available; they return `-1` on Windows and macOS.
- `rescan()` clears the cached list so the next call reads devices again.

The index `i` is zero-based and must be between `0` and `getCount() - 1`.

### `idLookup`

Loads the PCI and USB ID databases from `ids/pci.ids` and `ids/usb.ids` (or
known system locations) and maps numeric IDs to readable names.

- `idLookup.pci()` loads PCI names and class names.
- `idLookup.usb()` loads USB names.
- `vendorName(vendorId)` returns a vendor name or `null` if it is not listed.
- `deviceName(vendorId, deviceId)` returns a device name or `null`.
- `className(classCode)` returns the PCI subclass or base class name, or
  `null` when no name is known.
- `vendorCount()` returns how many vendor records were loaded.

### `sysFile`

An internal helper for resolving and reading Linux `/proc` and `/sys` files.
It provides text, line, and number readers. Missing or unreadable files return
empty text, an empty line list, or `-1` for a number rather than throwing an
exception.

The `hw.root` Java system property can point these readers at copied test data.
For example, `-Dhw.root=/home/me/sample` redirects a read of `/proc/cpuinfo` to
`/home/me/sample/proc/cpuinfo`.

## Platform notes

- CPU and memory readers use Linux `/proc` files; they do not provide those
  values on Windows or macOS.
- PCI and USB readers have additional Windows and macOS implementations.
- Linux-style USB bus and device numbers are unavailable on Windows and macOS.
- A missing ID database prevents the demo from translating IDs and results in
  an `IOException`.
