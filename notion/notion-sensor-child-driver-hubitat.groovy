/**
 *  Notion Sensor (child) Driver
 *  Author: David Pasirstein
 *
 *  Copyright (c) 2021 David Pasirstein
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *
 *  This is a child driver and not intended to be used directly.  Both this driver and the
 *  parent driver must be installed.  The parent Notion Driver will use this driver to create
 *  the discovered sensor devices and update data accordingly.
 *
*/

public static String version()      {  return '0.0.2'  }

metadata {
    definition (name: 'Notion Sensor (child) Driver',
            namespace: 'dpasirst',
            author: 'Dave Pasirstein',
            importUrl: '') {
        capability 'Sensor'
        capability "TemperatureMeasurement"
        capability "PresenceSensor"
        capability "Battery"
        capability "WaterSensor"
        capability "SignalStrength"
        capability "SmokeDetector"
        capability "ContactSensor"

        attribute 'sensor_id', 'string'
        attribute 'current_poll_update', 'string'
        attribute 'last_reported_at', 'string'
        attribute 'signal_strength', 'number'
        attribute 'firmware_version', 'string'
        attribute 'hardware_revision', 'number'

        command 'refresh'
    }

    preferences() {
        input 'tscale', 'enum', title: "Temperature Reporting", description: "Will Change on Next Poll\nunit of measurement", required: true, defaultValue: 'system default', options: ['system default','celsius','fahrenheit']
    }
}

void refresh() {
    def parent = getParent()
    parent?.refreshSensorAndTasks(device.currentValue("sensor_id"))
}

void installed() {
    state.comment1 = "To reset this driver, delete it.  It will be recreated on the next poll of the parent driver. " +
            "If you change your notion sensor configuration, you may need to do this to clear stale, no longer " +
            "used sensor data."
    state.comment2 = "Notion to Hubitat Battery Levels: high=100, medium=50, low=15, critical=1, -not reported-=0"
}

/**
 * this function is called by the parent Notion driver when getnotion.com is polled
 * which may occur on a full poll or an individual sensor device refresh
 * @param sensor a json parsed object returned from api.getnotion.com one level below "sensors:"
 * containing a single sensor object
 * @param tasks a json parsed object returned from api.getnotion.com one level below "tasks:"
 * this may contain one or many tasks, it will process tasks that match the sensor_id and
 * ignore those that do not
 */
void updateSensor(Object sensor, Object tasks) {
    if (sensor == null) {
        return
    }
    def now = new Date()
    sendEvent(name: 'current_poll_update', value: now.getTime())
    if (sensor.id != null) {
        sendEvent(name: 'sensor_id', value: sensor.id)
    }
    if (sensor.last_reported_at != null) {
        sendEvent(name: 'last_reported_at', value: sensor.last_reported_at)
    }
    if (sensor.signal_strength != null) {
        sendEvent(name: 'signal_strength', value: sensor.signal_strength)
    }
    if (sensor.rssi != null) {
        sendEvent(name: 'rssi', value: sensor.rssi)
    }
    if (sensor.lqi != null) {
        sendEvent(name: 'lqi', value: sensor.lqi)
    }
    if (sensor.firmware_version != null) {
        sendEvent(name: 'firmware_version', value: sensor.firmware_version)
    }
    if (sensor.hardware_revision != null) {
        sendEvent(name: 'hardware_revision', value: sensor.hardware_revision)
    }
    for (task in tasks) {
        if (task.sensor_id == null || task.sensor_id != sensor?.id) {
            continue
        }
        def val = findStateValue(task)
        if (task.task_type == "missing") {
            //first presence
            val = (val == "not_missing") ? "present" : "not present"
            sendEvent(name: 'presence', value: val)
        } else if (task.task_type == "leak") {
            val = (val == "no_leak") ? "dry" : "wet"
            sendEvent(name: 'water', value: val)
        } else if (task.task_type == "temperature") {
            if (val != null) {
                val = new BigDecimal(val)
                if (getTemperatureScale() == "F" || tscale == 'fahrenheit') {
                    val = celsiusToFahrenheit(val)
                }
            } else {
                val == new BigDecimal(0)
            }
            sendEvent(name: 'temperature', value: val)
        } else if (task.task_type == "low_battery") {
            if (val == "high") {
                val = 100
            } else if (val == "medium"){
                val = 50
            } else if (val == "low"){
                val = 15
            } else if (val == "critical"){
                val = 1
            } else {
                val = 0
            }
            sendEvent(name: 'battery', value: val)
        } else if (task.task_type == "alarm") {
            if (val == "no_alarm") {
                val = "clear"
            } else {
                val = "detected"
            }
            sendEvent(name: 'smoke', value: val)
        } else if (task.task_type == "door" ||
                task.task_type == "sliding" ||
                task.task_type == "safe" ||
                task.task_type == "garage_door" ||
                task.task_type == "window_hinged_horizontal" ||
                task.task_type == "window_hinged_vertical") {
            if (val != "closed") {
                val = "open"
            }
            sendEvent(name: 'contact', value: val)
        } //else {
            //log.warn "${device.label} Does not have support for ${task.task_type} with value: ${val}"
        //}

    }
}

def findStateValue(Object task) {
    return (task?.status?.insights?.primary?.to_state) ? task?.status?.insights?.primary?.to_state : task?.status?.value
}
