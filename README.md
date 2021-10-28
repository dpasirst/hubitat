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

## *Unofficial* Dyson Pure Hot Cool Link driver
This driver is intended to work with Dyson Pure Cool Link/Dyson Pure Cool Link Desk/Dyson Pure Hot Cool Link.
However, I have only tested it with the latter, an HP02 unit.

Instructions:
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
