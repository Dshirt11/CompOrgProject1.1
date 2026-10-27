# CS4421 hardware inspector: starter code

Plain Java classes that read hardware information from Linux files under
`/proc` and `/sys`. No C or native library is needed. Run the program on Linux,
in WSL, in a Linux virtual machine, or on the course cloud server.

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
how to call each class; it is not a solution to the assignment. Run it on Linux
or WSL to read CPU, memory, PCI, and USB information.
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
an empty string).

| Class | Methods | Source |
| --- | --- | --- |
| `cpuInfo` | `getCount()`, `getModel()`, `getField(name)`, `getBusy(core)`, `getIdle(core)` | `/proc/cpuinfo`, `/proc/stat` |
| `memInfo` | `getTotal()`, `getAvailable()`, `getUsed()`, `field(name)` (all in KiB) | `/proc/meminfo` |
| `pciInfo` | `getCount()`, `getAddress(i)`, `getBus(i)`, `getDevice(i)`, `getFunction(i)`, `getVendorId(i)`, `getDeviceId(i)`, `getClassCode(i)`, `rescan()` | `/sys/bus/pci/devices` |
| `usbInfo` | `getCount()`, `getName(i)`, `getVendorId(i)`, `getProductId(i)`, `getManufacturer(i)`, `getProduct(i)`, `getBusNumber(i)`, `getDeviceNumber(i)`, `rescan()` | `/sys/bus/usb/devices` |
| `idLookup` | `pci()`, `usb()`, `vendorName(id)`, `deviceName(vendor, device)`, `className(classCode)` | `ids/pci.ids`, `ids/usb.ids` |
| `sysFile` | internal helper used by the classes above | |

Notes:

- `getBusy` and `getIdle` count ticks since boot. To get the CPU load, read both
  twice, about a second apart, and work from the differences.
- On arm64 there is no `model name` in `/proc/cpuinfo`: `getModel()` returns
  "unknown". Use `getField("CPU part")` or `getField("CPU implementer")`.
- On Linux, `usbInfo` lists root hubs as well as plugged-in devices, in the
  same order as `lsusb`; `getBusNumber(i)` and `getDeviceNumber(i)` give the
  numbers that `lsusb` prints as "Bus 001 Device 002".
- A virtual machine or the cloud server may have no USB devices, so
  `getCount()` can be 0.
- `idLookup` methods return `null` when an ID is not in the file.
- The demo looks up PCI vendor and device names in `ids/pci.ids`; when an ID has
  no matching name, it displays the raw hexadecimal ID instead.
- USB vendor and product names are looked up in `ids/usb.ids`; unknown IDs are
  displayed as raw hexadecimal values.

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
