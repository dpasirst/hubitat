/**
 *  Notion Sensor (child) Driver - UNOFFICIAL
 *  Author: David Pasirstein
 *
 *  Copyright (c) 2021-2023 David Pasirstein
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
 * Some of this code references work:
 * Copyright (c) 2019-2023 Aaron Bach MIT License
 * https://github.com/bachya/aionotion
 *
*/

public static String version()      {  return '0.1.1'  }

metadata {
    definition (name: 'Notion Sensor (child) Driver',
            namespace: 'dpasirst',
            author: 'Dave Pasirstein',
            importUrl: 'https://raw.githubusercontent.com/dpasirst/hubitat/main/drivers/notion/notion-sensor-child-driver-hubitat.groovy') {
        capability 'Sensor'
        capability "Initialize"
        capability "Refresh"
        capability "TemperatureMeasurement"
        capability "PresenceSensor"
        capability "Battery"
        capability "WaterSensor"
        capability "SignalStrength"
        capability "SmokeDetector"
        capability "ContactSensor"

        attribute 'sensor_id', 'string'
        attribute 'sensor_uuid', 'string'
        attribute 'current_poll_update', 'string'
        attribute 'last_reported_at', 'string'
        attribute 'signal_strength', 'number'
        attribute 'firmware_version', 'string'
        attribute 'hardware_revision', 'number'

        attribute "presence_did_change", "enum", ["true", "false"]
        attribute "presence_change_last_reported_at", "string"
        attribute "water_did_change", "enum", ["true", "false"]
        attribute "water_change_last_reported_at", "string"
        attribute "temperature_did_change", "enum", ["true", "false"]
        attribute "temperature_change_last_reported_at", "string"
        attribute "battery_did_change", "enum", ["true", "false"]
        attribute "battery_change_last_reported_at", "string"
        attribute "smoke_did_change", "enum", ["true", "false"]
        attribute "smoke_change_last_reported_at", "string"
        attribute "contact_did_change", "enum", ["true", "false"]
        attribute "contact_change_last_reported_at", "string"

    }
preferences() {
        input 'tscale', 'enum', title: "Temperature Reporting", description: "Will Change on Next Poll\nunit of measurement", required: true, defaultValue: 'system default', options: ['system default','celsius','fahrenheit']
    }
}

void refresh() {
    def parent = getParent()
    parent?.refreshSensorAndTasks(device.currentValue("sensor_id"))
}

void initialize() {
    installed()
}

void installed() {
    state.comment1 = "To reset this driver, delete it.  It will be recreated on the next poll of the parent driver. " +
            "If you change your notion sensor configuration, you may need to do this to clear stale, no longer " +
            "used sensor data."
    state.comment2 = "Notion to Hubitat Battery Levels: high=100, medium=50, low=15, critical=1, -not reported-=0"
    state.comment3 = "Change events such as \"battery_did_change\" will toggle true/false since the last update/refresh if " +
            "the task had reported that an update occurred. The state itself may have already return to the original " +
            "value between polling / refresh but the change event will still indicate it had occurred."
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
void updateSensor(Object sensor, Object listeners) {
    // from: https://github.com/bachya/aionotion/blob/dev/aionotion/sensor/models.py#L145
    def SensorTypes = [
        BATTERY: 0,
        MOLD: 2,
        TEMPERATURE: 3,
        LEAK_STATUS: 4,
        SAFE: 5,
        DOOR: 6,
        SMOKE: 7,
        CONNECTED: 10,
        HINGED_WINDOW: 12,
        GARAGE_DOOR: 13,
        SLIDING_DOOR_OR_WINDOW: 32,
        UNKNOWN: 99
    ]
    if (sensor == null) {
        return
    }
    def now = new Date()
    sendEvent(name: 'current_poll_update', value: now.getTime())
    if (sensor.id != null) {
        sendEvent(name: 'sensor_id', value: sensor.id)
    }
    if (sensor.uuid != null) {
        sendEvent(name: 'sensor_uuid', value: sensor.uuid)
    }
    if (sensor.last_reported_at != null) {
        sendEvent(name: 'last_reported_at', value: sensor.last_reported_at)
    }
    if (sensor.signal_strength != null) {
        sendEvent(name: 'signal_strength', value: sensor.signal_strength)
    }
    if (sensor.firmware_version != null) {
        sendEvent(name: 'firmware_version', value: sensor.firmware_version)
    }
    if (sensor.hardware_revision != null) {
        sendEvent(name: 'hardware_revision', value: sensor.hardware_revision)
    }
    for (listener in listeners) {
        if (listener.sensor_id == null || listener.sensor_id != sensor?.uuid) {
            continue
        }
        def val = findStateValue(listener)
        def valUpdated = findTaskLastReported(sensor, listener)
        if (listener.definition_id == SensorTypes.CONNECTED) {
            //first presence
            val = (val == "not_missing") ? "present" : "not present"
            sendTaskUpdate('presence', val, valUpdated)
        } else if (listener.definition_id == SensorTypes.LEAK_STATUS) {
            val = (val == "no_leak") ? "dry" : "wet"
            sendTaskUpdate('water', val, valUpdated)
        } else if (listener.definition_id == SensorTypes.TEMPERATURE) {
            // seems findStateValue may fail us in this case, so we explicitly
            // will attempt to obtain the values
            val = listener?.status_localized?.state
            if (val != null) {
                def matcher = (val =~ /\d+/)
                if (matcher.find()) {
                    val = matcher.group().toInteger()
                } else {
                    val = 0
                }
            } else {
                val == new BigDecimal(0)
            }
            sendTaskUpdate('temperature', val, valUpdated)
            val = listener?.insights?.mold_risk?.value
            if (val != null) {
                if (listener?.insights?.mold_risk?.data_received_at) {
                    valUpdated = listener?.insights?.mold_risk?.data_received_at
                }
                sendTaskUpdate('mold_risk', val, valUpdated)
            }
            val = listener?.insights?.freeze?.value
            if (val != null) {
                if (listener?.insights?.freeze?.data_received_at) {
                    valUpdated = listener?.insights?.freeze?.data_received_at
                }
                sendTaskUpdate('freeze', val, valUpdated)
            }
        } else if (listener.definition_id == SensorTypes.BATTERY) {
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
            sendTaskUpdate('battery', val, valUpdated)
        } else if (listener.definition_id == SensorTypes.SMOKE) {
            if (val == "no_alarm") {
                val = "clear"
            } else {
                val = "detected"
            }
            sendTaskUpdate('smoke', val, valUpdated)
        } else if (listener.definition_id == SensorTypes.DOOR ||
                listener.definition_id == SensorTypes.SLIDING_DOOR_OR_WINDOW ||
                listener.definition_id == SensorTypes.SAFE ||
                listener.definition_id == SensorTypes.GARAGE_DOOR ||
                listener.definition_id == SensorTypes.HINGED_WINDOW
        ) {
            if (val != "closed") {
                val = "open"
            }
            sendTaskUpdate('contact', val, valUpdated)
        } //else {
            //log.warn "${device.label} Does not have support for ${task.task_type} with value: ${val}"
        //}

    }
}

def findStateValue(Object listener) {
    return (listener?.insights?.primary?.value) ? listener?.insights?.primary?.value : listener?.status_localized?.state
}

def findTaskLastReported(Object sensor, Object listener) {
    return (listener?.insights?.primary?.data_received_at) ? listener?.insights?.primary?.data_received_at : sensor?.last_reported_at
}
def sendTaskUpdate(String capability_name, Object val, Object date) {
    sendEvent(name: capability_name, value: val)
    sendEvent(name: capability_name + "_did_change",
            value: (device.currentValue(capability_name + "_change_last_reported_at") == date) ? "false" : true)
    sendEvent(name: capability_name + "_change_last_reported_at", value: date)
}
/*
         [
            id:9463db70-77f0-4f5e-a5f0-5159cea20000,
            definition_id:10,
            created_at:2021-08-06T13:44:33.721Z,
            type:sensor, model_version:2.0,
            sensor_id:a47a866d-c0c4-41bb-9881-9e6724039680,
            status_localized:[
                state:Connected,
                description:Nov 7 at 7:22pm
            ],
            insights:[
                primary:[
                    origin:[
                        id:19992762-c119-49c8-bff7-4f636cdc2577,
                        type:Sensor
                    ],
                    value:not_missing,
                    data_received_at:2023-11-06T21:25:19.611Z
                ]
            ],
            configuration:[:],
            pro_monitoring_status:ineligible
        ],
        [
            id:98a9c2d2-5835-431d-9661-438ea92cda79,
            definition_id:24,
            created_at:2019-10-19T14:44:22.624Z,
            type:sensor,
            model_version:1.0,
            sensor_id:a47a866d-c0c4-41bb-9881-9e6724039680,
            status_localized:[state:Unknown, description:Jun 8 at 3:47pm],
            insights:[
                primary:[
                    origin:null,
                    value:null,
                    data_received_at:null
                ]
            ],
            configuration:[:],
            pro_monitoring_status:ineligible
        ],
        [
            id:dd0eb248-7f43-42bf-8d15-e99a85140afa,
            definition_id:4,
            created_at:2020-10-16T00:11:23.434Z,
            type:sensor, model_version:2.2,
            sensor_id:a47a866d-c0c4-41bb-9881-9e6724039680,
            status_localized:[
                state:No Leak,
                description:Nov 8 at 11:08am
            ],
            insights:[
                primary:[
                    origin:[
                        id:68a590be-ff58-4d7f-b8c8-23b8a5b6d026,
                        type:Sensor
                    ],
                    value:no_leak,
                    data_received_at:2023-11-05T16:05:22.473Z
                ]
            ],
            configuration:[:],
            pro_monitoring_status:eligible
        ],
        [
            id:297edf2a-73f9-430a-892c-47f1998872d8,
            definition_id:3,
            created_at:2023-02-24T18:01:05.654Z,
            type:sensor,
            model_version:3.0,
            sensor_id:a47a866d-c0c4-41bb-9881-9e6724039680,
            status_localized:[
                state:70°,
                description:12:12pm
            ],
            insights:[
                primary:[
                    origin:[:],
                    value:,
                    data_received_at:2020-11-04T00:52:43.387Z
                ]
            ],
            configuration:[
                lower:14.44,
                upper:31.11,
                offset:0.0
            ],
            pro_monitoring_status:eligible
        ]

 */
