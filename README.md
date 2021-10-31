# hubitat

This repo contains some stuff for hubitat.  Consider it mostly hobby and likely buggy.

Please see the LICENSE file for license details

## *Unofficial* Notion driver
Both the main driver and the child driver must be installed.  The main (parent) Notion Driver will use the Notion Sensor child driver to create the discovered sensor devices and update data accordingly.

Instructions:
- Login to your Hubitat and go to Advanced -> Drivers Code
- Choose new driver to add this driver and then do it again for the child Notion Sensor driver
- Go to Devices -> Add Virtual Device - give it a name and select this driver
- Save and configure


## *Unofficial* Dyson Air Treatment Drivers
I have posted code for 3 Dyson Air Treatment Drivers; however, I have only tested one of the drivers and
with only a single device.  Code supporting other devices were based on my interpretation of code, postings, and
discussion from others.  It may not work.  There may be problems.

The drivers are as follows:
### *Unofficial* Dyson Pure Hot Cool Link driver: dyson-pure-hotcool-link-driver.groovy
This driver is intended to work with Dyson Pure Cool Link/Dyson Pure Cool Link Desk/Dyson Pure Hot Cool Link.
However, I have only tested it with the latter, an HP02 unit.

Devices this driver tries to support (the number coosponds to the last part of the number at the end of the
Device's WiFi SSID sticker):
- PURE COOL LINK "475"
- PURE COOL LINK DESK "469"
- PURE HOT COOL LINK "455" or "455A"

### *Unofficial* Dyson Pure Hot Cool driver: dyson-pure-hotcool-driver.groovy
This driver is intended to work with Dyson Pure Cool/Dyson Pure Cool Desk/Dyson Pure Cool Formaldehyde/Dyson Pure Hot Cool.
I do not own or have access to any of these units.  I do not know if this code will work.

Devices this driver tries to support (the number coosponds to the last part of the number at the end of the
Device's WiFi SSID sticker):
- PURE COOL "438"
- PURE COOL FORMALDEHYDE "438E"
- PURE COOL DESK "520"
- PURE HOT COOL "527" or "527E"

### *Unofficial* Dyson Pure Humidify Cool driver: dyson-pure-humidify-driver.groovy
This driver is intended to work with Dyson Pure Humidify Cool.
I do not own or have access to this type of unit.  I do not know if this code will work.

Devices this driver tries to support (the number coosponds to the last part of the number at the end of the
Device's WiFi SSID sticker):
- PURE HUMIDIFY COOL "358"


### Instructions:
- Login to your Hubitat and go to Advanced -> Drivers Code
- Choose new driver to add this driver
- Go to Devices -> Add Virtual Device - give it a name and select this driver
- Save and configure

To use this with Rule Machine, it will present itself as a Fan and Thermostat; however,
there is more functionality available through driver specific commands.  These are accessable
in Rule Machine via Run Custom Action -> Actuator.  Then any custom command can be selected.
If the selected custom command takes a parameter on the drivers page, the same parameter
value can be specified here. Note: specify "string" - all custom commands for this driver
assume the data entered are strings.
