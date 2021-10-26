/**
 *  Dyson Pure Cool Link/Pure Hot Cool Link Hubitat Driver - UNOFFICIAL
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
 *  This is an UNOFFICIAL Driver.
 *  Dyson has many devices that should be able to follow a similar pattern however,
 *  they will NOT work with this driver.  This because other dyson devices use different
 *  mapping of the parameter names.
 *
 *  WARNING: This Driver code is only lightly tested, while it works for me, it may not work for you,
 *  it may cause damage or worse. You have been warned.
 *
 *  Instructions:
 *  - Login to your Hubitat and go to Advanced -> Drivers Code
 *  - Choose new driver to add this driver
 *  - Go to Devices -> Add Virtual Device - give it a name and select this driver
 *  - Save and configure
 *
 *
 *  THANKS:
 *  Home Assistant had a head start.  Thank You
 *  @AlmostSerious Marcus Peters 
 *   https://community.home-assistant.io/t/dyson-pure-cool-link-local-mqtt-control/217263
 *  @shenex Xiaonan Shen
 *   https://github.com/shenxn/libdyson for ideas and know how
 *
 */

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.security.MessageDigest

public static String version()      {  return '0.0.2'  }

def static fanModeMap() {["Off":"OFF","Fan":"FAN","Auto":"AUTO"]}
def static fanSpeedMap() {["1":"0001","2":"0002","3":"0003","4":"0004","5":"0005","6":"0006",
                           "7":"0007","8":"0008","9":"0009","10":"0010","Auto":"AUTO"]}
def static qualityTargetMap() {["Off":"OFF","Good":"0004","Sensitive":"0003",
                                 "Default":"0002","Very Sensitive":"0001"]}
def static oscillationMap() {["On":"ON","Off":"OFF"]}
def static nightModeMap() {["On":"ON","Off":"OFF"]}
def static focusModeMap() {["On":"ON","Off":"OFF"]}
def static heatModeMap() {["Off":"OFF","Heat":"HEAT"]}
def static resetFilterMap() {["Do Nothing":"STET","Reset":"RSTF"]}

metadata {
    definition (name: 'Dyson Pure Hot Cool Link Driver (e.g., HP02)',
            namespace: 'dpasirst',
            author: 'Dave Pasirstein',
            importUrl: 'https://raw.githubusercontent.com/dpasirst/hubitat/main/dyson/dyson-pure-hotcool-link-driver.groovy') {
        capability "Actuator"
        capability 'Sensor'
        capability "Polling"
        capability "AirQuality"
        capability "FilterStatus"
        capability "ThermostatHeatingSetpoint"



        attribute 'current_dysonpoll', 'string'
        //attribute 'current_dyson_lastauth', 'string'
        attribute 'dyson_dyson_mqtt_rcv', 'string'
        attribute 'fanMode', 'enum', fanModeMap().values().toList() //fmod
        attribute 'oscillation', 'enum', oscillationMap().values().toList() //oson
        attribute 'nightMode', 'enum', nightModeMap().values().toList()  //nmod
        attribute 'fanSpeed', 'enum', fanSpeedMap().values().toList() //fnsp
        attribute 'fanState', 'enum', ["OFF","FAN"]  //fnst
        attribute 'qualityTarget', 'enum', qualityTargetMap().values().toList() //qtar :OFF,GOOD,SENSITIVE,DEFAULT,VerySensitive
        attribute 'standbyMonitoring', 'enum', ["ON","OFF"]  //rhtm
        attribute 'focusMode', 'enum', focusModeMap().values().toList()  //ffoc
        attribute 'tiltState', 'enum', ["TILT","OK"]  //tilt
        attribute 'heatMode', 'enum', heatModeMap().values().toList()  //hmod
        attribute 'heatState', 'enum', ["OFF","HEAT"]  //heat status? hsta
        attribute 'heatTempTarget', 'number'  //heat target? hmax (note number)
        attribute 'heatTempTargetFriendly', 'number'  //friendly heat target (note number)
        attribute 'countryCode', 'string'

        attribute 'errorCode', 'string' //ercd
        attribute 'warningCode', 'string' //wacd
        //not listed: reset filter life: rstf="RSTF" (reset) / "STET" (do nothing)
        attribute 'filterLifeHrs','number' //filf (note number)

        //enviornmental
        attribute 'temperature', 'string'  //friendly? tact in kelvin --env (OFF,INIT,FAIL,value)
        attribute 'temperatureFriendly', 'number'  //friendly number
        attribute 'humidityPercentage', 'string' //hact  --env (OFF,INIT,FAIL,value)
        attribute 'humidityPercentageFriendly', 'number' //hact number
        attribute 'particulateMatter', 'string' //pact  --env (OFF,INIT,FAIL,value)
        attribute 'volatileOrganicCompounds', 'string' //vact  --env (OFF,INIT,FAIL,value)
        attribute 'sleepTimer', 'string' //sltm  OFF or 0 < duration <= 540 formatted as "0000"


        //command 'refreshDeviceList'
        command 'connectToUnit'
        command 'disconnectFromUnit'
        command 'requestStateUpdate'
        command 'setFanSpeed',[[name: "New Speed", type:"ENUM",
                                            constraints: fanSpeedMap().keySet().toList()]]
        command 'setFanMode',[[name: "Change Mode", type:"ENUM",
                                constraints: fanModeMap().keySet().toList()]]
        command 'setHeatMode',[[name: "Change Mode", type:"ENUM",
                               constraints: heatModeMap().keySet().toList()]]
        command 'setNightMode',[[name: "Change Mode", type:"ENUM",
                                constraints: nightModeMap().keySet().toList()]]
        command 'setOscillationMode',[[name: "Change Mode", type:"ENUM",
                                constraints: oscillationMap().keySet().toList()]]
        command 'setFocusMode',[[name: "Change Mode", type:"ENUM",
                                       constraints: focusModeMap().keySet().toList()]]
        command 'resetFilter',[[name: "Select Command", type:"ENUM",
                                description: "Warning this will send a reset filter command to the device",
                                 constraints: resetFilterMap().keySet().toList()]]
    }

    preferences() {
        section('Query Inputs'){
            //input 'email', 'text', required: true, defaultValue: '', title: "email", description: "Type Your Dyson Online Email/Username Here"
            //input 'password', 'password', required: true, defaultValue: '', title: "password", description: "Type Your Dyson Online password Here"
            //input 'countryCode', 'text', title: "Country Code (ISO 3166-1 alpha-2)", required: true, defaultValue: 'US', description: "Type Your 2 Character Country Code Here"
            input 'unitSSID', 'text', required: true, defaultValue: '', title: "WiFi SSID", description: "WiFi SSID on the sticker of the Dyson Unit (not your home's ssid)"
            input 'unitPassword', 'password', required: true, defaultValue: '', title: "Unit Password", description: "Dyson Unit WiFi password on the sticker (not your Dyson Online password)"
            input 'unitAddress', 'text', required: true, defaultValue: '', title: "Unit IP Address", description: "The Network IP Address of the unit on your home network"
            input 'pollInterval', 'enum', title: "Notion Poll Interval", required: true, defaultValue: 'Manual Poll Only', options: ['Manual Poll Only','1 Minute','5 Minutes', '10 Minutes', '15 Minutes', '30 Minutes', '1 Hour', '3 Hours']
        }
    }
}

//for now we only support a limited number of devices for two reasons...
// first, it appears that other connected Dyson device using a different mapping
// for the device params (see DYSON_PARAM_COOL_STATE_MAP and DYSON_PARAM_HOT_COOL_STATE_MAP)
// second, I don't own any of those other devices and I don't have a reasonable way to
// develop for it.
def isSupportedCoolDevice(String device_type) {
    return [DEVICE_TYPE_PURE_COOL_LINK(),DEVICE_TYPE_PURE_COOL_LINK_DESK()].contains(device_type)
}
def isSupportedHotCoolDevice(String device_type) {
    return [DEVICE_TYPE_PURE_HOT_COOL_LINK()].contains(device_type)
}

void updated(){
    switch (pollInterval) {
        case '1 Minute'     : runEvery1Minute(runPoll);   break;
        case '5 Minutes'    : runEvery5Minutes(runPoll);  break;
        case '10 Minutes'   : runEvery10Minutes(runPoll); break;
        case '15 Minutes'   : runEvery15Minutes(runPoll); break;
        case '30 Minutes'   : runEvery30Minutes(runPoll); break;
        case '1 Hour'       : runEvery1Hour(runPoll);     break;
        case '3 Hours'      : runEvery3Hours(runPoll);    break;
        default             : unschedule(runPoll);        break;
    }
}
def poll(){
    runPoll()
}

/*
def refreshDeviceList() {
    loginDysonOnline()
}
*/

void runPoll() {
    if (!interfaces.mqtt.isConnected()) {
        connectToUnit()
        if (!interfaces.mqtt.isConnected()) {
            return
        }
    }
    pollState()
}


private DEVICE_TYPE_360_EYE() {"N223"}
private DEVICE_TYPE_360_HEURIST() {"276"}
private DEVICE_TYPE_PURE_COOL_LINK() {"475"}
private DEVICE_TYPE_PURE_COOL_LINK_DESK() {"469"}
private DEVICE_TYPE_PURE_COOL() {"438"}
private DEVICE_TYPE_PURE_COOL_FORMALDEHYDE() {"438E"}
private DEVICE_TYPE_PURE_COOL_DESK() {"520"}
private DEVICE_TYPE_PURE_HUMIDIFY_COOL() {"358"}
private DEVICE_TYPE_PURE_HOT_COOL_LINK() {"455"}
private DEVICE_TYPE_PURE_HOT_COOL() {"527"}
private DEVICE_TYPE_PURE_HOT_COOL_NEW() {"527E"}

//I read that for some units, the model in the WiFi SSID is does not match the model needed for MQTT.
private static DEVICE_TYPE_MAP() {
    ["455A":"455"]
}

private DEVICE_TYPE_NAMES()  {[
        "${DEVICE_TYPE_360_EYE()}": "360 Eye robot vacuum",
        "${DEVICE_TYPE_360_HEURIST()}": "360 Heurist robot vacuum",
        "${DEVICE_TYPE_PURE_COOL()}": "Pure Cool",
        "${DEVICE_TYPE_PURE_COOL_FORMALDEHYDE()}": "Pure Cool Formaldehyde",
        "${DEVICE_TYPE_PURE_COOL_DESK()}": "Pure Cool Desk",
        "${DEVICE_TYPE_PURE_COOL_LINK()}": "Pure Cool Link",
        "${DEVICE_TYPE_PURE_COOL_LINK_DESK()}": "Pure Cool Link Desk",
        "${DEVICE_TYPE_PURE_HOT_COOL()}": "Pure Hot+Cool",
        "${DEVICE_TYPE_PURE_HOT_COOL_NEW()}": "Pure Hot+Cool (New)",
        "${DEVICE_TYPE_PURE_HOT_COOL_LINK()}": "Pure Hot+Cool Link",
        "${DEVICE_TYPE_PURE_HUMIDIFY_COOL()}": "Pure Humidify+Cool",
]}

private static DYSON_PARAM_COOL_STATE_MAP()  {
    [
            'fanMode': "fmod", //fan
            'oscillation': "oson", //fan
            'nightMode': "nmod", //fan
            'fanSpeed': "fnsp", //fan
            'fanState': "fnst", //fan
            'qualityTarget': "qtar", //fan
            'standbyMonitoring': "rhtm", //fan

            'errorCode' : "ercd", //all?
            'warningCode' : "wacd", //all?
            'filterLifeHrs':"filf", //fan
    ]
}
private static DYSON_PARAM_HOT_COOL_STATE_MAP()  {
    [
            'focusMode':"ffoc", //heat
            'tiltState':"tilt", //heat
            'heatMode':"hmod", //heat
            'heatState':"hsta", //heat
            'heatTempTarget':"hmax", //heat
    ]
}
private static DYSON_PARAM_ENVIRONMENTAL_MAP()  {
    [
            //environmental are contained in a "data" sub object in json
            'temperature': "tact", //fan
            'humidityPercentage':"hact", //fan
            'particulateMatter':"pact", //fan
            'volatileOrganicCompounds': "vact", //fan
            'sleepTimer': "sltm" //fan
    ]
}


private static DYSON_MQTT_SUBSRIPTION_TOPIC(String model, String serial) { "${model}/${serial}/status/current" }
private static DYSON_MQTT_PUBLISH_TOPIC(String model, String serial) { "${model}/${serial}/command" }
private UNIT_SSID() { unitSSID }
private UNIT_PASSWD() { unitPassword }
private UNIT_ADDRESS() { unitAddress }

private DYSON_MQTT_MSG_CURRENT_STATE() {"CURRENT-STATE"}
private DYSON_MQTT_MSG_ENVIRONMENTAL_CURRENT() {"ENVIRONMENTAL-CURRENT-SENSOR-DATA"}
private DYSON_MQTT_MSG_STATE_CHANGE() {"STATE-CHANGE"}
private DYSON_MQTT_MSG_STATE_SET() {"STATE-SET"}
private DYSON_MQTT_MSG_SND_GET_ENVIRONMENTAL() {"REQUEST-PRODUCT-ENVIRONMENT-CURRENT-SENSOR-DATA"}
private DYSON_MQTT_MSG_SND_GET_CURRENT() {"REQUEST-CURRENT-STATE"}

/**
 * method to connect and subscribe on the device
 */
void connectToUnit() {
    if (!interfaces.mqtt.isConnected()) {
        def serial = ""
        def device_type = ""
        def hashedPass = hashDevicePassword(UNIT_PASSWD())
        (serial,device_type) = decodeSSID(UNIT_SSID())
        if (!isSupported()) {
            log.error("DYSON unsupported device, will not attempt to connect")
            return
        }
        log.debug("DYSON:init mqtt to: tcp://${UNIT_ADDRESS()}:1883")
        interfaces.mqtt.connect("tcp://${UNIT_ADDRESS()}:1883",device.deviceNetworkId,serial,hashedPass)
        log.debug("DYSON reporting isConnected=${interfaces.mqtt.isConnected()}")
        if (interfaces.mqtt.isConnected()) {
            log.debug("DYSON Initiating Subscription: ${DYSON_MQTT_SUBSRIPTION_TOPIC(device_type,serial)}")
            interfaces.mqtt.subscribe(DYSON_MQTT_SUBSRIPTION_TOPIC(device_type,serial))
        }
    }
}
/**
 * unsubscribe and disconnect from the device
 */
void disconnectFromUnit() {
    if (interfaces.mqtt.isConnected()) {
        def serial = ""
        def device_type = ""
        (serial,device_type) = decodeSSID(UNIT_SSID())
        log.info("DYSON MQTT Disconnect initiated")
        log.debug("DYSON Initiating UnSubscribe: ${DYSON_MQTT_SUBSRIPTION_TOPIC(device_type,serial)}")
        interfaces.mqtt.unsubscribe(DYSON_MQTT_SUBSRIPTION_TOPIC(device_type,serial))
        try { interfaces.mqtt.disconnect() } catch (e) {}
        log.debug("Disconnected")
    }
}

/**
 *
 * @param devicePassword from the WiFi Sticker on the dyson device (NOT your Dyson Online password)
 * @return a sha-512 calculation
 */
def hashDevicePassword(String devicePassword) {
    MessageDigest digest = MessageDigest.getInstance("SHA-512")
    byte[] encodedhash = digest.digest(devicePassword.getBytes("UTF-8"))
    return encodedhash.encodeBase64().toString()
}

/**
 * test to see if this driver supports the Dyson device
 * @return boolean true if this driver supports the device, otherwise false
 */
def isSupported() {
    if (isSupportedCoolDevice(state.device_type)) {
        log.info("Dyson device is supported Cool")
        state.canHeat = 0
    } else if (isSupportedHotCoolDevice(state.device_type)) {
        log.info("Dyson device is supported Hot+Cool")
        state.canHeat = 1
    } else {
        log.error("Device is NOT SUPPORTED by this driver")
        return false
    }
    return true
}

/**
 * Will decode required values from the WiFi SSID on the sticker of the Dyson device
 * @param ssid - the WiFi SSID from the sticker on the device (NOT your home wifi)
 * @return [serial,device_type] tuple
 */
def decodeSSID(String ssid) {
    try {
        def serial = ""
        def device_type = ""
        if ((match = ssid =~ /^DYSON-([0-9A-Z]{3}-[A-Z]{2}-[0-9A-Z]{8})-([0-9]{3}[A-Z]?)$/ )) {
            serial = match.group(1)
            device_type = DEVICE_TYPE_MAP().getOrDefault((String)match.group(2),(String)match.group(2))
            log.info("Dyson is believed to be: ${DEVICE_TYPE_NAMES().getOrDefault("${device_type}",device_type)}")
            log.debug("Dyson Determined serial:${serial} topic model:${device_type}")
            state.device_type = device_type
            state.device_name = DEVICE_TYPE_NAMES().getOrDefault("${device_type}",device_type)
            state.device_serial = serial
            return [serial,device_type]
        } else {
            log.error("DYSON WiFi SSID could not be parsed (format?, typo?) which is required to connect to device")
        }
    } catch(e) {
        log.error("DYSON WiFi SSID could not be parsed (format?, typo?) which is required to connect to device",e)
    }
    return [null,null]
}

/**
 * Standard Hubitat function called automatically for MQTT messages
 * @param message
 * @return nothing
 */
def parse(String message) {
    //log.debug("Received Dyson message: $message")
    def mqttmessage = interfaces.mqtt.parseMessage(message)
    //log.debug("Dyson MQTT Message has ${mqttmessage.size()} elements: $mqttmessage")
    def payload = (new JsonSlurper().parseText((String)mqttmessage.get("payload"))) as Map
    processMessage(payload)
}

/**
 * This method will parse/process the payload field of a dyson MQTT message
 * it will handle current state, state change, and environmental
 * @param dysonJSON as a Map (must be a Map) of the Payload field
 * @return nothing
 */
def processMessage(Map dysonJSON) {
    if (dysonJSON?.msg == null) { return }
    sendEvent(name: 'dyson_dyson_mqtt_rcv', value: mqttDate())
    if (dysonJSON.msg == DYSON_MQTT_MSG_CURRENT_STATE()) {
        log.debug("Dyson Received a current state message")
        def productState = dysonJSON.getOrDefault("product-state",null) as Map
        if (productState != null) {
            DYSON_PARAM_COOL_STATE_MAP().each { key, attName ->
                def val = productState.getOrDefault(attName,null)
                if (val != null) {
                    if (attName == DYSON_PARAM_COOL_STATE_MAP().filterLifeHrs) {
                        sendEvent(name: "filterStatus",
                                  value: ((val as String).toInteger() > 0 ) ? "normal" : "replace")
                    }
                    sendEvent(name: key, value: val)
                }
            }
            if (state.canHeat) {
                DYSON_PARAM_HOT_COOL_STATE_MAP().each { key, attName ->
                    def val = productState.getOrDefault(attName, null)
                    if (val != null) {
                        if (attName == DYSON_PARAM_HOT_COOL_STATE_MAP().heatTempTarget) {
                            def nval = KelvinToCelsius(val)
                            if (getTemperatureScale() == "F") {
                                nval = celsiusToFahrenheit(nval)
                            }
                            sendEvent(name: "${key}Friendly", value: nval)
                        }
                        sendEvent(name: key, value: val)
                    }
                }
            }
        }
    } else if(dysonJSON.msg == DYSON_MQTT_MSG_ENVIRONMENTAL_CURRENT()) {
        log.debug("Dyson Received an environmental message")
        def data = dysonJSON.getOrDefault("data",null) as Map
        if (data != null) {
            DYSON_PARAM_ENVIRONMENTAL_MAP().each { key, attName ->
                def val = data.getOrDefault(attName, null)
                if (val != null) {
                    if (attName == DYSON_PARAM_ENVIRONMENTAL_MAP().temperature) {
                        def nval = KelvinToCelsius(val)
                        if (getTemperatureScale() == "F") {
                            nval = celsiusToFahrenheit(nval)
                        }
                        sendEvent(name: "${key}Friendly", value: nval)
                    } else if ([DYSON_PARAM_ENVIRONMENTAL_MAP().humidityPercentage].contains(attName)) {
                        def nval = val.toString().toInteger()
                        sendEvent(name: "${key}Friendly", value: nval)
                    }
                    if (attName == DYSON_PARAM_ENVIRONMENTAL_MAP().particulateMatter) {
                        def particulates = val.toString().toInteger()
                        if (particulates > 500) { particulates = 500 }
                        sendEvent(name: "airQualityIndex", value: particulates)
                    }
                    sendEvent(name: key, value: val)
                }
            }
        }
    } else if (dysonJSON.msg == DYSON_MQTT_MSG_STATE_CHANGE()) {
        log.debug("Dyson Received a state changed message")
        def productState = dysonJSON.getOrDefault("product-state",null) as Map
        if (productState != null) {
            DYSON_PARAM_COOL_STATE_MAP().each { key, attName ->
                def val = (productState.getOrDefault(attName,null) as ArrayList)?.last()
                if (val != null) {
                    if (attName == DYSON_PARAM_COOL_STATE_MAP().filterLifeHrs) {
                        sendEvent(name: "filterStatus",
                                value: ((val as String).toInteger() > 0 ) ? "normal" : "replace")
                    }
                    sendEvent(name: key, value: val)
                }
            }
            if (state.canHeat) {
                DYSON_PARAM_HOT_COOL_STATE_MAP().each { key, attName ->
                    def val = (productState.getOrDefault(attName,null) as ArrayList)?.last()
                    if (val != null) {
                        if (attName == DYSON_PARAM_HOT_COOL_STATE_MAP().heatTempTarget) {
                            def nval = KelvinToCelsius(val)
                            if (getTemperatureScale() == "F") {
                                nval = celsiusToFahrenheit(nval)
                            }
                            sendEvent(name: "${key}Friendly", value: nval)
                        }
                        sendEvent(name: key, value: val)
                    }
                }
            }
        }
    } else {
        log.debug("Dyson Received a msg type that we don't handle: ${dysonJSON.msg} -- ${dysonJSON}")
    }
}

/**
 * Hubitat will send status info to this method Error's start with Error and Status starts with Status
 * @param message per Hubitat's documentation
 * @return nothing
 */
def mqttClientStatus(String message) {
    if (message.startsWith("Error")) {
        log.error("DYSON MQTT: ${message}")
        if (!interfaces.mqtt.isConnected()) {
            log.debug("Dyson Uh...looks like we lost our MQTT connection")
        }
    } else if (message.startsWith("Status")) {
        log.info("DYSON MQTT: ${message}")
    } else {
        log.error("Dyson Unexpected status message: ${message}")
        if (!interfaces.mqtt.isConnected()) {
            log.debug("Dyson Uh...looks like we lost our MQTT connection")
        }
    }
}

/**
 * utility to generate a date in the format used by Dyson's MQTT
 * @return a string date in ISO 8601'esk format
 */
def mqttDate() {
    def now = new Date()
    return now.format("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", TimeZone.getTimeZone('UTC'))
}

//command requestStateUpdate
def requestStateUpdate() {
    if (interfaces.mqtt.isConnected()) {
        pollState()
    } else {
        log.error("Manual request for state update is not possible because MQTT is not connected")
    }
}

/**
 * This will send a request msg to get the current state (which may return state and environmental)
 * It will then send a second request msg to get environment (so env may be returned twice)
 * @return nothing
 */
def pollState() {
    if (interfaces.mqtt.isConnected()) {
        def stateMsg = ["msg": DYSON_MQTT_MSG_SND_GET_CURRENT(),"time":mqttDate()]
        interfaces.mqtt.publish(DYSON_MQTT_PUBLISH_TOPIC((String)state.device_type, (String)state.device_serial),
                    JsonOutput.toJson(stateMsg))
        def envMsg = ["msg": DYSON_MQTT_MSG_SND_GET_ENVIRONMENTAL(),"time":mqttDate()]
        interfaces.mqtt.publish(DYSON_MQTT_PUBLISH_TOPIC((String)state.device_type, (String)state.device_serial),
                JsonOutput.toJson(envMsg))
    }
}

/**
 * this will send an MQTT communication update to the connected device
 * @param configParams Map of params and values to send in the "data" field
 * @return nothing
 */
def setConfiguration(Map configParams) {
    if (configParams != null && interfaces.mqtt.isConnected()) {
        def message = JsonOutput.toJson(["msg":DYSON_MQTT_MSG_STATE_SET(),
                                                     "time":mqttDate(),
                                                     "mode-reason": "LAPP",
                                                     "data":configParams])
        log.debug("DYSON Sending:${message}")
        interfaces.mqtt.publish(DYSON_MQTT_PUBLISH_TOPIC((String)state.device_type, (String)state.device_serial),
                                message)
    }
}

//command
def setFanSpeed(speed) {
    def newSpeed = fanSpeedMap().getOrDefault(speed,"AUTO")
    if (newSpeed != "AUTO") {
        def config = [(DYSON_PARAM_COOL_STATE_MAP().fanSpeed): newSpeed]
        log.info("DYSON Set Fan Speed: ${config}")
        setConfiguration(config)
    } else {
        //a little bit of trickery here because AUTO is not a speed but a mode.
        setFanMode("Auto") //we really want it cased this way
    }
}
//command
def setFanMode(mode) {
    def newMode = fanModeMap().getOrDefault(mode,"OFF")
    def config = [(DYSON_PARAM_COOL_STATE_MAP().fanMode): newMode]
    log.info("DYSON Set Fan Mode: ${config}")
    setConfiguration(config)
}
//command
def setNightMode(mode) {
    def newMode = nightModeMap().getOrDefault(mode,"OFF")
    def config = [(DYSON_PARAM_COOL_STATE_MAP().nightMode): newMode]
    log.info("DYSON Set Night Mode: ${config}")
    setConfiguration(config)
}
//command
def setOscillationMode(mode) {
    def newMode = oscillationMap().getOrDefault(mode,"OFF")
    def config = [(DYSON_PARAM_COOL_STATE_MAP().oscillation): newMode]
    log.info("DYSON Set Oscillation Mode: ${config}")
    setConfiguration(config)
}
//command
def setHeatMode(mode) {
    if (state.canHeat) {
        def newMode = heatModeMap().getOrDefault(mode, "OFF")
        def config = [(DYSON_PARAM_HOT_COOL_STATE_MAP().heatMode): newMode]
        log.info("DYSON Set Heat Mode: ${config}")
        setConfiguration(config)
    } else {
        log.info("DYSON Set Heat Mode is not possible with this device")
    }
}
//command
def setFocusMode(mode) {
    if (state.canHeat) {
        def newMode = focusModeMap().getOrDefault(mode, "OFF")
        def config = [(DYSON_PARAM_HOT_COOL_STATE_MAP().focusMode): newMode]
        log.info("DYSON Set Focus Mode: ${config}")
        setConfiguration(config)
    } else {
        log.info("DYSON Set Focus Mode is not possible with this device")
    }
}
//command
def resetFilter(mode) {
    def newMode = resetFilterMap().getOrDefault(mode, "STET")
    def config = ["rstf": newMode]
    log.info("DYSON Reset Filter Mode: ${config}")
    setConfiguration(config)
}

/**
 * Heat Target for fan. Note dyson uses kelvin as the temperature unit.
 * Convert the given int celsius temperature to string in Kelvin.
 * @param temperature temperature in celsius between 1 to 37 inclusive.
 * @return temperature in kelvin
 */
String HeatTargetFromCelsius(BigDecimal temperature) {
    if (temperature < 1 || temperature > 37) {
        //we could error hear, but we will target approx room temp
        temperature = 21.5
    }
    return ((int)((temperature + 273) * 10)).toString()
}
/**
 * Heat Target for fan. Note dyson uses kelvin as the temperature unit.
 * Convert the given int fahrenheit temperature to string in Kelvin.
 * @param temperature temperature in fahrenheit between 34 to 98 inclusive.
 * @return temperature in kelvin
 */
String HeatTargetFromFahrenheit(BigDecimal temperature) {
    return HeatTargetFromCelsius( fahrenheitToCelsius(temperature) )
}

/**
 *
 * @param temperature string that will be converted to an Integer
 * @return number decimal value in celsius
 */
static def KelvinToCelsius(temperature) {
    return (temperature.toInteger()/10) - 273
}

////////////////////////////////////////
// <<<<<<<  Thermostat Commands >>>>>>>
////////////////////////////////////////
/**
 * Hubitat capability "ThermostatHeatingSetpoint" has setHeatingSetpoint(temperature)
 * with corresponding command button
 * @param temperature string in the unit (F/C) used by this Hubitat
 * @return nothing
 */
def setHeatingSetpoint(temperature) {
    //temperature required(NUMBER, unit:°F || °C) - Heating setpoint in degrees
    //attrib: heatingSetpoint
    if (state.canHeat) {
        def kelvinStr = (temperatureScale == "F") ?
                HeatTargetFromFahrenheit(temperature.toString().toBigDecimal()) :
                HeatTargetFromCelsius(temperature.toString().toBigDecimal())
        def config = [(DYSON_PARAM_HOT_COOL_STATE_MAP().heatMode) : heatModeMap().Heat,
                      (DYSON_PARAM_HOT_COOL_STATE_MAP().heatTempTarget): kelvinStr]
        log.info("DYSON Heat Target: ${config}")
        setConfiguration(config)
    } else {
        log.info("DYSON Heat Target is not possible with this device")
    }
}


/**
 *
 * @param json_payload from Dyson Online
 * @return Return true if this json payload is a Dyson 360 Eye device
 */
def is_360_eye_device(json_payload) {
    return (json_payload?.ProductType == DEVICE_TYPE_360_EYE()) ? true : false
}
/**
 *
 * @param product_type Dyson device model
 * @return Return True if device_model support heating mode, else False.
 */
def support_heating(String product_type) {
    return DEVICE_TYPE_NAMES().getOrDefault(product_type,"").contains("Hot")
}

/**
 *
 * @param json_payload from Dyson Online
 * @return true if this json payload is a hot+cool device.
 */
def is_heating_device(json_payload) {
    return support_heating(json_payload['ProductType'])
}

/*
This is commented out because dyson now uses a two factor approach for authenticating to it's servers
and retrieving devices.  Thus, the implementation below, no longer works.  It looks possible to
support the new flow which looks something like:
1) send account email to dyson
2) dyson sends email with one-time-code
3) send account with password & one-time-code
4) obtain dyson api password (aka token) to retrieve devices

Also, Dyson does not appear to be using a classic public CA cert.
Instead it seems to be using a ICA DigiCert TLS RSA SHA256 2020 CA1
- https://cacerts.digicert.com/DigiCertTLSRSASHA2562020CA1-1.crt.pem

Thus, we either need to pin this cert for the trust chain or disable validation.

private DYSON_API_SERVER()  { "api.cp.dyson.com" }

void loginDysonOnline() {
    if( email== null || password== null || countryCode == null ) {
        log.warn 'Dyson Driver - WARNING: Dyson email/password/country not found.  Please configure in preferences.'
        return;
    }

    def ParamsGN;
    ParamsGN = [
            uri:  "https://${DYSON_API_SERVER()}/v1/userregistration/authenticate?country=${countryCode}",
            requestContentType: "application/json",
            contentType: "application/json",
            body: [ 'Email' : email, 'Password' : password ]
    ]
    //log.debug('Auth dyson: ' + ParamsGN.uri)
    asynchttpPost('dysonLoginHandler', ParamsGN)
    return;
}

void dysonLoginHandler(resp, data) {
    log.debug('Login Dyson online resp')

    if(resp.getStatus() < 200 || resp.getStatus() >= 300) {
        log.warn 'Calling ' + "auth"//atomicState.gn_base_uri
        log.warn resp.getStatus() + ':' + resp.getErrorMessage()
    } else {
        def now = new Date();
        sendEvent(name: 'current_dyson_lastauth', value: now.getTime());
        def json = parseJson(resp.data)
        def respAccount = json?.Account
        def respPass = json?.Password
        if (respAccount != null && respPass != null) {
            log.debug("Login Dyson online successful Account:${respAccount}, retrieving devices")
            dysonRetrieveDevices(respAccount,respPass)
        } else {
            log.error("Login Dyson online unsuccessful Account:${respAccount}")
        }
    }
}

void dysonRetrieveDevices(String account, String pass) {
    if (account == null || pass == null) {
        return
    }

    def ParamsGN;
    ParamsGN = [
            uri: "https://${DYSON_API_SERVER()}/v1/provisioningservice/manifest",
            contentType: 'application/json',
            headers: [ 'Authorization':"Basic "+ "${account}:${pass}".bytes.encodeBase64().toString() ]
    ]
    try {
        httpGet(ParamsGN) { resp ->
            //log.debug('Task Dyson device list resp')

            if(resp.getStatus() < 200 || resp.getStatus() >= 300) {
                log.warn 'Retrieving devices task failed'
                log.warn resp.getStatus() + ':' + resp.getErrorMessage()
            } else {
                //log.warn "Tasks: " + resp.data
                //result = parseJson(resp.data) -- this is already parsed
                log.debug("Retrieved ${resp.data?.length} devices")
                def dyson_device_list = new ArrayList()
                if (resp.data != null) {
                    for (device in resp.data) {
                        if (is_360_eye_device(device)) {
                            Log.error("Dyson Device is 360_eye UNSUPPORTED: ${device}")
                        } else if (is_heating_device(device)) {
                            Log.info("Found Dyson Pure Hot Cool Link: ${device}")
                            dyson_device_list.add(device)
                        } else {
                            Log.error("Dyson Device is UNSUPPORTED (regular Cool Link?): ${device}")
                        }
                    }
                    if (dyson_device.size() > 0) {
                        state.known_dyson_devices = dyson_device_list
                    }
                }
            }
        }
    } catch (Exception e) {
        log.warn "Call to on failed: ${e.message}"
    }
    return
}
*/
