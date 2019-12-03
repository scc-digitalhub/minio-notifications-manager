package it.smartcommunitylab.minionotificationsmanager.mqtt;

import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import it.smartcommunitylab.minionotificationsmanager.service.NotificationService;

@Component
public class MqttNotificationsRouter {
    private final static Logger _log = LoggerFactory.getLogger(MqttNotificationsRouter.class);

    @Value("${mqtt.enable}")
    private boolean ENABLE;

    @Value("${mqtt.broker}")
    private String BROKER;

    @Value("${mqtt.username}")
    private String USERNAME;

    @Value("${mqtt.password}")
    private String PASSWORD;

    @Value("${mqtt.identity}")
    private String IDENTITY;

    @Value("${mqtt.topic}")
    private String TOPIC;

    @Value("${mqtt.qos}")
    private int QOS;

    @Autowired
    NotificationService service;

    private IMqttClient _client;
    private IMqttAsyncClient _aclient;
    private MqttMessageHandler handler;

    private MqttCallbackExtended callback = new MqttCallbackExtended() {
        @Override
        public void connectionLost(Throwable t) {
            _log.debug("callback connectionLost");
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
        }

        @Override
        public void connectComplete(boolean reconnect, String serverURI) {
            _log.debug("callback connectComplete, reconnect? " + String.valueOf(reconnect));

            if (reconnect) {
                try {
                    subscribe();
                } catch (MqttException e) {
                    e.printStackTrace();
                }
            }
        }

    };

    @PostConstruct
    public void init() {
        _log.debug("init mqtt router");

        if (ENABLE && !BROKER.isEmpty() && !TOPIC.isEmpty()) {
            try {
                if (IDENTITY.isEmpty()) {
                    // generate with random
                    IDENTITY = "mqtt-router-" + RandomStringUtils.randomAlphanumeric(5);
                }

                // create client
                _log.debug("create client for " + BROKER);
                getReceiveClient();
                getSendClient();

                _log.debug("connect client");
                connect();

                // subscribe handler
                _log.debug("subscribe to " + TOPIC);
                subscribe();
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }

    @PreDestroy
    public void cleanup() throws Exception {
        _log.debug("cleanup mqtt router");
        if (_client != null) {
            if (_client.isConnected()) {
                _log.debug("disconnect client from broker");
                disconnect();
            }
        }
    }

    private MqttMessageHandler handler() throws MqttException {
        if (handler == null) {
            _log.debug("build handler");
            handler = new MqttMessageHandler(getSendClient(), service, TOPIC, QOS);
        }

        return handler;
    }

    private void subscribe() throws MqttException {
        // subscribe to base topic and pass service+client
        // TODO implement cache for lookups, async processing via bus
        getReceiveClient().subscribe(TOPIC, QOS, handler());
    }

    private void connect() throws MqttSecurityException, MqttException {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);

        _client.setCallback(callback);
        _client.connect(options);

        // no callback for send client, nothing to subscribe
        _aclient.connect(options);

    }

    private void disconnect() throws MqttException {
        _client.disconnect();
        _aclient.disconnect();
    }

    private IMqttClient getReceiveClient() throws MqttException {
        if (_client == null) {
            String clientId = IDENTITY + "-recv";

            _log.debug("create receive client for " + BROKER + " as " + clientId);
            _client = new MqttClient(BROKER, clientId);
        }
        return _client;
    }

    private IMqttAsyncClient getSendClient() throws MqttException {
        if (_aclient == null) {
            String clientId = IDENTITY + "-send";

            // disable persistance of messages to file, keep only memory
            _log.debug("create send client for " + BROKER + " as " + clientId);
            _aclient = new MqttAsyncClient(BROKER, clientId, null);
        }
        return _aclient;

    }
}
