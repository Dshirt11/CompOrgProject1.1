# Running the hardware inspector

This project reads hardware information from the operating system. It does not
ask you to type hardware values: on Linux and WSL it reads them from `/proc`
and `/sys`. Run commands from the project folder, the one containing `src` and
`ids`.

## Run in WSL

Open your WSL terminal. If Java is not installed, install a JDK:

```bash
sudo apt update
sudo apt install default-jdk
```

Change to the project folder. For this workspace, the Windows `C:` drive is
mounted under `/mnt/c`:

```bash
cd "/mnt/c/Users/..."
```

Compile all Java source files, then run the demo:

```bash
mkdir -p out
javac -d out src/*.java
java -cp out demo
```

The `ids` folder must be in the current working directory when you run the
program; it contains the PCI and USB name databases used by the demo.
On Linux, the report samples CPU load for about one second before displaying
the hardware inventory.

## Save a text report

Pass `--report` followed by an output filename. The report is written as UTF-8
text. A relative filename is created in the current project folder; existing
files at that path are overwritten.

```bash
java -cp out demo --report hardware-report.txt
cat hardware-report.txt
```

You can also pass a full output path:

```bash
java -cp out demo --report "$HOME/hardware-report.txt"
```

## Filter the device lists

Filters can be combined. Vendor matching ignores case and accepts part of a
vendor name or ID. `--class` applies to PCI devices and accepts part of a PCI
class name or the beginning of its hexadecimal class code. `--bus` accepts a
decimal bus number and matches PCI and USB bus numbers where available.

```bash
java -cp out demo --vendor Intel
java -cp out demo --class network
java -cp out demo --class 02
java -cp out demo --bus 1
java -cp out demo --type usb
java -cp out demo --type pci --vendor NVIDIA --bus 1
java -cp out demo --class display --report display-devices.txt
```

Use `--type all`, `--type pci`, or `--type usb` to select which device list is
shown. CPU and memory details are still included when available. A class
filter is PCI-only, so it hides the USB list; combining `--class` with
`--type usb` is rejected. Filters can also be combined with `--report` to save
the filtered output.

PCI devices are grouped into network, display, storage, bridge, and USB
controller sections. Remaining PCI devices are grouped by their PCI class.
Each section shows its device count, and the report includes totals for each
PCI class. Devices within a group are sorted by vendor.

## Call the hardware functions

The hardware information classes expose static methods, so you can call them
directly from another Java class. For example, save this as
`HardwareExample.java` in the project folder:

```java
public class HardwareExample
{
    public static void main(String[] args) throws Exception
    {
        System.out.println("CPU: " + cpuInfo.getModel());
        System.out.println("Logical cores: " + cpuInfo.getCount());
        System.out.println("Memory total (KiB): " + memInfo.getTotal());
        System.out.println("Memory used (KiB): " + memInfo.getUsed());

        idLookup pciNames = idLookup.pci();
        for (int i = 0; i < pciInfo.getCount(); i++) {
            int vendorId = pciInfo.getVendorId(i);
            int deviceId = pciInfo.getDeviceId(i);
            System.out.println("PCI " + pciInfo.getAddress(i) + ": "
                + pciNames.vendorName(vendorId) + " "
                + pciNames.deviceName(vendorId, deviceId));
        }

        idLookup usbNames = idLookup.usb();
        for (int i = 0; i < usbInfo.getCount(); i++) {
            int vendorId = usbInfo.getVendorId(i);
            int productId = usbInfo.getProductId(i);
            System.out.println("USB: "
                + usbNames.vendorName(vendorId) + " "
                + usbNames.deviceName(vendorId, productId));
        }
    }
}
```

Compile and run the example from the project folder:

```bash
javac -cp out -d out HardwareExample.java
java -cp out HardwareExample
```

Useful methods include:

- `cpuInfo.getCount()`, `getModel()`, `getField(name)`, `getBusy(core)`,
  `getIdle(core)`
- `memInfo.getTotal()`, `getAvailable()`, `getUsed()`, `field(name)`
- `pciInfo.getCount()`, `getAddress(i)`, `getVendorId(i)`, `getDeviceId(i)`,
  `getClassCode(i)`, `rescan()`
- `usbInfo.getCount()`, `getVendorId(i)`, `getProductId(i)`,
  `getManufacturer(i)`, `getProduct(i)`, `rescan()`
- `idLookup.pci()` or `idLookup.usb()`, followed by `vendorName(id)` or
  `deviceName(vendorId, deviceId)`

The index `i` in device methods ranges from `0` to `getCount() - 1`. Some
methods, such as CPU and memory readers, depend on Linux `/proc` files; run in
WSL or another Linux environment to use them.

## Test with copied system data

To read data copied from another Linux machine, place the corresponding
`proc` and `sys` files under a root folder, then set `hw.root` when launching:

```bash
java -Dhw.root=/path/to/copied-data -cp out demo
```

The copied data should contain `proc/cpuinfo`, `proc/stat`, `proc/meminfo` and
the relevant `sys/bus` directories.

## Launch the graphical interface

The command-line interface remains the default. Add `--gui` to launch the
optional Java Swing interface instead:

```bash
java -cp out demo --gui
```

The window has CPU, memory, PCI, and USB tabs. Each tab has a text filter that
searches every column, and column headers can be clicked to sort the table.
Choose **Export visible tables...** to save the currently displayed (filtered
and sorted) rows from all tabs to a UTF-8 text file. The GUI gathers hardware
information when it opens. A graphical display is required; when running WSL,
use a WSLg-enabled setup or another display server.
