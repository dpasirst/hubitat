/**
 *  Notion Driver
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
 *  Both this driver and the child driver must be installed.  This parent Notion Driver will
 *  use the Notion Sensor child driver to create the discovered sensor devices and update
 *  data accordingly.
 *
 *  Instructions:
 *  - Login to your Hubitat and go to Advanced -> Drivers Code
 *  - Choose new driver to add this driver and then do it again for the child Notion Sensor driver
 *  - Go to Devices -> Add Virtual Device - give it a name and select this driver
 *  - Save and configure
 */
public static String version()      {  return '0.0.2'  }

metadata {
    definition (name: 'Notion Driver (https://getnotion.com/)',
            namespace: 'dpasirst',
            author: 'Dave Pasirstein',
            importUrl: '') {
        capability 'Sensor'

        attribute 'current_notionpoll', 'string'
        attribute 'current_notion_lastauth', 'string'

        command 'reAuth'
        command 'pollData'
        command 'resetToken'
    }

    preferences() {
        section('Query Inputs'){
            input 'email', 'text', required: true, defaultValue: '', title: "email", description: "Type Your Notion Username (email) Here"
            input 'password', 'password', required: true, defaultValue: '', title: "password", description: "Type Your Notion password Here"
            input 'pollInterval', 'enum', title: "Notion Poll Interval", required: true, defaultValue: '10 Minutes', options: ['Manual Poll Only','1 Minute','5 Minutes', '10 Minutes', '15 Minutes', '30 Minutes', '1 Hour', '3 Hours']
        }
    }
}

void updated(){
    switch (pollInterval) {
        case '1 Minute'     : runEvery1Minute(pollNotion);   break;
        case '5 Minutes'    : runEvery5Minutes(pollNotion);  break;
        case '10 Minutes'   : runEvery10Minutes(pollNotion); break;
        case '15 Minutes'   : runEvery15Minutes(pollNotion); break;
        case '30 Minutes'   : runEvery30Minutes(pollNotion); break;
        case '1 Hour'       : runEvery1Hour(pollNotion);     break;
        case '3 Hours'      : runEvery3Hours(pollNotion);    break;
        default             : unschedule(pollNotion);        break;
    }
}

void setupDevice(Map map){
    device.updateSetting("email",         [value: map.email,    type: "string"]);
    device.updateSetting("password",      [value: map.password,  type: "password"]);
    device.updateSetting("pollInterval",  [value: map.pollInterval, type: "string"]);

    updated();
}

void pollData(){
    pollNotion();
}

void reAuth() {
    resetToken()
    loadAuthToken();
}

void resetToken() {
    state.remove("accessToken")
}

// ------- Begin Notion (api.getnotion.com) Poll Routines -------
void loadAuthToken() {
    if( email== null || password== null ) {
        log.warn 'getNotion.com Driver - WARNING: Notion email/password not found.  Please configure in preferences.'
        return;
    }

    def ParamsGN;
    ParamsGN = [
            uri:  "https://api.getnotion.com/api/users/sign_in",
            requestContentType: "application/json",
            contentType: 'application/json',
            body: [ 'sessions': [ 'email' : email, 'password' : password ] ]
    ]
    //log.debug('Auth getnotion.com: ' + ParamsGN.uri)
    asynchttpPost('notionAuthHandler', ParamsGN)
    return;
}

void notionAuthHandler(resp, data) {
    log.debug('Auth getnotion.com resp')

    if(resp.getStatus() < 200 || resp.getStatus() >= 300) {
        log.warn 'Calling ' + "auth"//atomicState.gn_base_uri
        log.warn resp.getStatus() + ':' + resp.getErrorMessage()
    } else {
        def now = new Date();
        sendEvent(name: 'current_notion_lastauth', value: now.getTime());
        //response structure:
        //'{"users":{"id":65894,"uuid":"c046a481-d084-4d73-bea2-8ecfeeeebce1","first_name":"givenname",
        // "last_name":"surname","email":"nobody@nowhere.com","phone_number":null,"role":"user",
        // "organization":"Notion User","authentication_token":"some-value",
        // "created_at":"2018-11-22T00:11:11.111Z","updated_at":"2021-02-15T00:01:11.111Z"},
        // "session":{"user_id":"c046a481-d084-4d73-bea2-8ecfeeeebce1",
        // "authentication_token":"some-value"}}'
        def json = parseJson(resp.data)
        state.user_uuid = json?.users?.uuid
        state.accessToken = json?.users?.authentication_token
        performPoll((String)json?.users?.authentication_token)
    }
}

String currentTokenOnPoll() {
    if (state.accessToken != null && state.accessToken.length() > 1) {
        return atomicState.accessToken
    } else {
        //accessToken is null so we need to load it then poll
        loadAuthToken()
    }
    return null
}


void pollNotion() {
    if (email == null || password == null) {
        log.warn 'getNotion.com Driver - WARNING: Notion email/password not found.  Please configure in preferences.'
        return
    }
    def token = currentTokenOnPoll()
    if (token != null) {
        performPoll(token)
    }
}

void performPoll(String accessToken) {
    if (accessToken == null || accessToken.length() < 2) {
        return
    }

    def ParamsGN;
    ParamsGN = [
            uri: "https://api.getnotion.com/api/sensors/",
            contentType: 'application/json',
            headers: [ 'Authorization':"Token token="+accessToken ]
    ]
    log.debug('Poll getnotion.com: ' + ParamsGN.uri)
    asynchttpGet('notionHandler', ParamsGN)
    return;
}


void notionHandler(resp, data) {
    //log.debug('Polling getnotion.com resp')

    if(resp.getStatus() < 200 || resp.getStatus() >= 300) {
        resetToken()
        log.warn 'Calling sensors failed, resetting accessToken which might help recover on next poll'
        log.warn resp.getStatus() + ':' + resp.getErrorMessage()
    } else {
        def now = new Date();
        sendEvent(name: 'current_notionpoll', value: now.getTime());
        //log.warn "SENSORS: " + resp.data
        def json = parseJson(resp.data)

        if (json != null) {
            def tasks = pollTasksSync(atomicState.accessToken)
            def child
            //log.debug "Server Returned ${json.sensors?.size()} sensors"
            for (sensor in json.sensors) {
                if (sensor.name != null && sensor.device_key != null) {
                    child = addChild((String) sensor.device_key,
                            (String) sensor.name,
                            "dpasirst",
                            'Notion Sensor (child) Driver',
                            false)
                    child.updateSensor(sensor, tasks?.tasks)
                }
            }
        }

    }
}

/**
 * this function is called by the child driver via the refresh command
 * @param sensorID the id value of the sensor to update
 */
void refreshSensorAndTasks(String sensorID) {
    pollSensor(atomicState.accessToken, sensorID)
}
//this function is used when the child driver via the refresh command
void pollSensor(String accessToken, String sensorID) {
    if (accessToken == null || accessToken.length() < 2 || sensorID == null || sensorID.length() < 1) {
        return
    }

    def ParamsGN;
    ParamsGN = [
            uri: "https://api.getnotion.com/api/sensors/"+sensorID,
            contentType: 'application/json',
            headers: [ 'Authorization':"Token token="+accessToken ]
    ]
    log.debug('Poll getnotion.com: ' + ParamsGN.uri)
    asynchttpGet('notionSensorHandler', ParamsGN)
    return;
}

//this function is used when the child driver via the refresh command
void notionSensorHandler(resp, data) {
    //log.debug('Sensor Polling getnotion.com resp')

    if(resp.getStatus() < 200 || resp.getStatus() >= 300) {
        log.warn 'Calling' + "sensor"
        log.warn resp.getStatus() + ':' + resp.getErrorMessage()
    } else {
        def json = parseJson(resp.data)
        String sensor = json?.sensors?.id
        String device_key = json?.sensors?.device_key

        if (json != null) {
            def tasks = pollTasksSync((String)atomicState.accessToken,(String)sensor)
            def child = childDevices?.find {(it.deviceNetworkId == device_key) }
            if (child != null) {
                log.debug "Updating Device ${device_key}"
                child.updateSensor(json.sensors, tasks?.tasks)
            } else {
                log.warn "Failed to Find Device for Update: ${device_key}"
            }
        }
    }
}

/**
 * the is a synchronous http request to obtain the notion tasks configured to the sensors
 * @param accessToken
 * @param sensorid this does ont seem to work, it was intended to limit the response to
 * only the sensord specified
 * @return already parsed json object containing all the current tasts
 */
Object pollTasksSync(String accessToken, String sensorid = null) {
    if (accessToken == null || accessToken.length() < 2) {
        return
    }

    def ParamsGN;
    def result;
    ParamsGN = [
            uri: "https://api.getnotion.com/api/tasks/",
            contentType: 'application/json',
            headers: [ 'Authorization':"Token token="+accessToken ]
    ]
    if (sensorid != null) {
        ParamsGN.body = [ sensor_id : [sensorid] ]
    }
    //log.debug('Poll tasks getnotion.com: ' + ParamsGN.uri)
    try {
        httpGet(ParamsGN) { resp ->
            //log.debug('Task Polling getnotion.com resp')

            if(resp.getStatus() < 200 || resp.getStatus() >= 300) {
                log.warn 'Retrieving tasks failed'
                log.warn resp.getStatus() + ':' + resp.getErrorMessage()
            } else {
                //log.warn "Tasks: " + resp.data
                //result = parseJson(resp.data) -- this is already parsed
                result = resp.data
            }
        }
    } catch (Exception e) {
        log.warn "Call to on failed: ${e.message}"
    }
    return result
}

String getNotionSensors(){
    return state.notionSensors;
}

String getNotionUser(){
    return state.notionUser;
}

def childExists(String device_id) {
    def children = childDevices
    def childDevice = children.find{ (it.deviceNetworkId == device_id) }
    if (childDevice) {
        return true
    }
    return false
}

private Object addChild(String device_id, label, namespace, driver, isComponent){
    def children = childDevices
    def childDevice = children.find{ (it.deviceNetworkId == device_id) }
    if (childDevice) {
        return childDevice
    } else {
        try {
            def newChild = addChildDevice(namespace, driver, device_id,
                    [name: "${device.displayName} (${label})", label: "${device.displayName} (${label})",
                     isComponent: isComponent])
            return newChild
        } catch (e) {
            runIn(3, "sendAlert", [data: [message: "Create child device failed. Make sure the driver for \"${driver}\" with a namespace of ${namespace} is installed"]])
        }
    }
    return null
}

private deleteChild(id){
    if(childExists(id)){
        def childDevice = childDevices.find{(it.deviceNetworkId == device_id)}
        try {
            if(childDevice) deleteChildDevice(childDevice.deviceNetworkId)
        } catch (e) {
            runIn(3, "sendAlert", [data: [message: "Failed to delete child device. Make sure the device is not in use by any App."]])
        }
    }
}
