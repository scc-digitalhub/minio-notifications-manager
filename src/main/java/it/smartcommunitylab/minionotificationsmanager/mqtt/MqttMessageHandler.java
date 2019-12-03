package it.smartcommunitylab.minionotificationsmanager.mqtt;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.smartcommunitylab.minionotificationsmanager.model.EventDTO;
import it.smartcommunitylab.minionotificationsmanager.service.NotificationService;

public class MqttMessageHandler implements IMqttMessageListener {
    private final static Logger _log = LoggerFactory.getLogger(MqttMessageHandler.class);

    private final NotificationService service;

    private final IMqttAsyncClient client;

    private final String TOPIC;
    private final int QOS;

    public MqttMessageHandler(IMqttAsyncClient c, NotificationService s, String baseTopic, int qos) {
        _log.debug("create message handler with service");
        client = c;
        service = s;
        TOPIC = baseTopic;
        QOS = qos;
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        _log.debug("message arrived for topic " + topic);
        // message is JSON

        String payload = new String(message.getPayload(), "UTF-8");
        JSONObject json = new JSONObject(payload);
        _log.trace("dump message " + json.toString(1));

        // extract records
        JSONArray records = json.getJSONArray("Records");
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.getJSONObject(i);
            String action = record.getString("eventName");

            JSONObject s3 = record.getJSONObject("s3");
            String bucket = s3.getJSONObject("bucket").getString("name");
            String key = s3.getJSONObject("object").getString("key");

            _log.debug("receive event " + action + " for bucket " + bucket + " key " + key);

            try {
                // cache topics to avoid double sending
                List<String> topics = new ArrayList<>();

                // fetch all events for bucket
                List<EventDTO> events = service.listEvents(bucket).stream()
                        .map(e -> EventDTO.fromEvent(e))
                        .collect(Collectors.toList());

                // search ALL matches
                for (EventDTO event : events) {
                    if (event.matchesAction(action) && event.matchesPrefix(key) && event.matchesSuffix(key)) {
                        // check if already delivered due to duplicate event registration
                        if (!topics.contains(event.getTopic())) {
                            // match, send message to topic
                            String dest = TOPIC + "/" + event.getTopic();

                            try {
                                _log.debug("route message to " + dest);

                                // build payload with this single record
                                JSONObject j = new JSONObject();
                                j.put("EventName", json.getString("EventName"));
                                j.put("Key", json.getString("Key"));
                                JSONArray r = new JSONArray();
                                r.put(record);
                                j.put("Records", r);

                                // send with no retain (ie no last will message)
                                // otherwise clients will receive the last event on new connection
                                client.publish(dest, j.toString().getBytes(), QOS, false);

                                // register delivery
                                topics.add(event.getTopic());
                            } catch (Exception pex) {
                                _log.error("error routing message to " + dest + ": " + pex.getMessage());
                                pex.printStackTrace();
                            }
                        }
                    }
                }

            } catch (Exception ex) {
                // discard
                ex.printStackTrace();
            }

        }
    }

}
