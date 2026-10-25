# Implemented extensions

This document records additions to the hardware inspector beyond the basic
hardware-reading example. It describes implemented features, not proposed
future ideas.

## Save the hardware inventory as a text report

The demo can save its formatted hardware summary to a UTF-8 text file. The
report includes the information the demo normally prints: CPU and memory
details where supported, PCI devices grouped by class with totals per class and
vendor, and USB device information.

### How to use it

Run from the project folder, which contains the `ids` directory:

```bash
java -cp out demo --report hardware-report.txt
```

The output path may be relative to the current folder or absolute. Parent
directories are created if necessary. If the destination file already exists,
it is overwritten. When export succeeds, the program prints the report path.

Without `--report`, the report is printed to the terminal as before. An
unsupported command-line argument produces a usage error.

### Implementation

`demo` parses the optional report argument, generates the report once, then
routes that report either to the terminal or to the requested file. This keeps
the terminal and saved report content consistent. The file is encoded as
UTF-8.

## Filter and sort device lists

The demo accepts optional filters to show only relevant devices:

- `--vendor <text>` matches part of the PCI or USB vendor name, ignoring case.
  If a vendor name is unavailable, the raw hexadecimal ID can be matched.
- `--class <text>` filters PCI devices by part of the class name or by a
  prefix of the hexadecimal class code. A class filter is PCI-only; the USB
  list is omitted, and `--type usb` cannot be combined with it.
- `--bus <number>` filters PCI and USB devices by decimal bus number. Devices
  without an available bus number do not match.
- `--type all|pci|usb` chooses which device list is shown. CPU and memory
  information remains in the report when available.

Filters can be combined, and they also apply when saving a text report. PCI
devices are grouped by category and sorted by vendor within each group. USB
devices are sorted by vendor name.

Examples:

```bash
java -cp out demo --vendor Intel
java -cp out demo --class network
java -cp out demo --bus 1 --type pci
java -cp out demo --type usb --report usb-inventory.txt
```

## Group PCI devices by class

PCI devices are grouped into the following sections using their PCI class
codes:

- Network controllers (`02xx`)
- Display controllers (`03xx`)
- Storage controllers (`01xx`)
- Bridges (`06xx`)
- USB controllers (`0c03xx`)

Devices outside those categories, or with an unavailable class code, are
listed under other PCI devices and grouped by their PCI base/subclass code.
Devices within each section are sorted by vendor name. Every section displays
its device count, followed by a totals-by-class summary and the overall PCI
device count. Existing vendor, class, and bus filters are applied before
grouping; selecting USB-only omits PCI sections.

The class filters can be used together with grouping, for example:

```bash
java -cp out demo --class network
java -cp out demo --type pci --vendor Intel --report pci-inventory.txt
```

## Optional graphical interface

The program can be run in its existing command-line mode or launched with a
Swing graphical interface. The command-line mode remains the default:

```bash
java -cp out demo
java -cp out demo --gui
```

The GUI provides CPU, memory, PCI, and USB tabs with table views. A filter box
on each tab searches all values in that table without regard to case, and
clicking a column header sorts the table. **Export visible tables...** saves
the currently filtered and sorted rows from every tab to a UTF-8 text file.
The export dialog asks before replacing an existing file. Hardware data is
collected once when the window opens; close and relaunch to refresh it.

The GUI uses Java Swing, so no additional GUI library is needed. A graphical
display is required. WSL users need WSLg or another configured display server.

### Limitations

- This exports plain text only; CSV and JSON export are not implemented.
- CPU and memory details still depend on Linux `/proc` files.
- PCI and USB contents depend on what the operating system exposes and on the
  required ID database files being available.

## Supporting documentation

These guides were added to make the project easier to use and understand:

- [RUNNING.md](RUNNING.md) explains WSL setup, compiling and running, report
  export, calling the reader methods, and testing with copied system data.
- [CODE-OVERVIEW.md](CODE-OVERVIEW.md) describes the classes, methods, data
  sources, and the overall program flow.

They document the project and do not add runtime features.
