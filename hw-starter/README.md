# CS4421 hardware inspector: starter code

Plain Java classes that read hardware information from the text files Linux
provides under `/proc` and `/sys`. No C and no native library is needed.
The assignment's hardware data sources are Linux files under `/proc` and
`/sys`. Run it on Linux, in a Linux virtual machine, or on the course cloud
server to use those sources. As an extra, `pciInfo` and `usbInfo` can also list
devices on Windows through Plug and Play and on macOS through `system_profiler`;
those platform-specific queries do not replace the assignment's Linux data
sources. CPU and memory readers remain Linux-only.

## Build and run

See [RUNNING.md](RUNNING.md) for the WSL setup, command examples, report export,
and examples of calling the hardware-reading methods.
See [CODE-OVERVIEW.md](CODE-OVERVIEW.md) for descriptions of the classes and
their functions.
See [EXTENSIONS.md](EXTENSIONS.md) for the implemented extensions and their
behavior.

    mkdir -p out
    javac -d out src/*.java
    java -cp out demo

Run `java` from this folder, so that the `ids` folder is found. `demo` shows
how to call each class; it is not a solution to the assignment. On Windows and
macOS, CPU and memory data remain Linux-only; PCI and USB devices are listed
using the platform's native hardware query. Bus and device numbers are shown
as unavailable outside Linux.
The demo groups PCI devices by controller category and PCI class, with counts
per class and vendor. USB devices are sorted by vendor and include their vendor
and product IDs; if none match the selected filters, it says so.

To save the same output as a UTF-8 text report instead of printing it to the
terminal, pass an output path:

    java -cp out demo --report hardware-report.txt

The report path can be relative to this project folder or absolute. Existing
files at that path are overwritten.
To open the optional graphical interface, run `java -cp out demo --gui`.

## The classes

All methods are `static`. Linux values that cannot be read come back as -1 (or
an empty string). A failed Windows or macOS PCI/USB query throws an
`IllegalStateException` rather than looking like an empty device list.

| Class | Methods | Source |
| --- | --- | --- |
| `cpuInfo` | `getCount()`, `getModel()`, `getField(name)`, `getBusy(core)`, `getIdle(core)` | `/proc/cpuinfo`, `/proc/stat` |
| `memInfo` | `getTotal()`, `getAvailable()`, `getUsed()`, `field(name)` (all in KiB) | `/proc/meminfo` |
| `pciInfo` | `getCount()`, `getAddress(i)`, `getBus(i)`, `getDevice(i)`, `getFunction(i)`, `getVendorId(i)`, `getDeviceId(i)`, `getClassCode(i)`, `rescan()` | Assignment/Linux: `/sys/bus/pci/devices`; optional Windows/macOS support: native OS APIs |
| `usbInfo` | `getCount()`, `getName(i)`, `getVendorId(i)`, `getProductId(i)`, `getManufacturer(i)`, `getProduct(i)`, `getBusNumber(i)`, `getDeviceNumber(i)`, `rescan()` | Linux: `/sys/bus/usb/devices`; Windows/macOS: native OS APIs |
| `idLookup` | `pci()`, `usb()`, `vendorName(id)`, `deviceName(vendor, device)`, `className(classCode)` | `ids/pci.ids`, `ids/usb.ids` |
| `sysFile` | internal helper used by the classes above | |

Notes:

- `getBusy` and `getIdle` count ticks since boot. To get the CPU load, read both
  twice, about a second apart, and work from the differences.
- On arm64 there is no `model name` in `/proc/cpuinfo`: `getModel()` returns
  "unknown". Use `getField("CPU part")` or `getField("CPU implementer")`.
- On Linux, `usbInfo` lists root hubs as well as plugged-in devices, in the
  same order as `lsusb`; `getBusNumber(i)` and `getDeviceNumber(i)` give the
  numbers that `lsusb` prints as "Bus 001 Device 002". Windows and macOS don't
  provide these Linux-style numbers, so those methods return -1 there.
- A virtual machine or the cloud server may have no USB devices, so
  `getCount()` can be 0.
- `idLookup` methods return `null` when an ID is not in the file.
- The demo looks up PCI vendor and device names in `ids/pci.ids`; when an ID has
  no matching name, it displays the raw hexadecimal ID instead.
- USB vendor and product names are looked up in `ids/usb.ids`; unknown IDs are
  displayed as raw hexadecimal values.
- On Windows, `pciInfo.getAddress(i)` uses the reported bus/device/function
  when available (with domain `0000`, which Windows does not expose here). If
  Windows does not report a location, it returns the device's Plug and Play ID
  and the corresponding number methods return `-1`. The Windows registry can
  retain entries for devices that were previously installed.
- On macOS, PCI addresses use domain `0000` when bus, device, and function
  numbers are reported. Apple silicon Macs may not expose PCI devices through
  `system_profiler`.
- Windows and macOS USB queries provide vendor and product IDs and names, but
  do not provide the Linux-style bus and device numbers used by `lsusb`.

## Testing against a copy of /proc and /sys

To test with data from another machine, copy `/proc/cpuinfo`, `/proc/stat`,
`/proc/meminfo` and the `/sys/bus` folders into a folder, and run with
`java -Dhw.root=/path/to/folder -cp out demo`.

## The ID files

`ids/pci.ids` is the PCI ID database (version 2026.10.12) from
https://pci-ids.ucw.cz/. `ids/usb.ids` is the USB ID list (version 2026.06.26)
from https://github.com/vcrhonek/hwdata. Both are distributed under the GNU
General Public License (version 2 or later) or the 3-clause BSD licence. Newer
copies can be downloaded from those sites.
