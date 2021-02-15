# hubitat

This repo contains some stuff for hubitat.  Consider it mostly hobby and likely buggy.

Please see the LICENSE file for license details

## *Unofficial* Notion driver
Both this driver and the child driver must be installed.  This parent Notion Driver will use the Notion Sensor child driver to create the discovered sensor devices and update data accordingly.

Instructions:
- Login to your Hubitat and go to Advanced -> Drivers Code
- Choose new driver to add this driver and then do it again for the child Notion Sensor driver
- Go to Devices -> Add Virtual Device - give it a name and select this driver
- Save and configure
