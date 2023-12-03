package be.domotiqueinformatique.tools;

import be.domotiqueinformatique.knx2mqtt.api.domain.KnxStatusDto;
import be.domotiqueinformatique.knx2mqtt.common.AbstractClientMQTTHandler;
import be.domotiqueinformatique.knx2mqtt.common.GroupAddressInfoManager;
import be.domotiqueinformatique.knx2mqtt.common.domain.GroupAddressInfoJson;
import be.domotiqueinformatique.knx2mqtt.common.domain.GroupAddressInfoListDto;
import be.domotiqueinformatique.knx2mqtt.common.domain.Knx2MqttEnum;
import be.domotiqueinformatique.tools.factory.EntitiesFactory;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5RetainHandling;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static be.domotiqueinformatique.knx2mqtt.common.domain.Knx2MqttEnum.TOPIC_GROUP_ADDRESS_UPDATE_INFO;

public class MQTTHandler extends AbstractClientMQTTHandler<GroupAddressInfoJson> {
    private final Logger L = Logger.getLogger(getClass().getName());
    private GroupAddressInfoListDto groupAddressInfoListDto = new GroupAddressInfoListDto();

    private MQTTHandler() {
        super("knx2mqtt_to_homekit2mqtt", new GroupAddressInfoManager<>(GroupAddressInfoJson.class));
    }

    public static void init() {
        setMqttInstance(new MQTTHandler());
        getMqttInstance().start();
    }

    @Override
    protected void doInit() {

        L.info("Connecting to MQTT broker " + mqttc.getConfig() + " and TOPIC PREFIX=" + topicPrefix);

        Mqtt5ConnAck connAck = mqttc.toBlocking().connectWith()
                .cleanStart(true)
                .sessionExpiryInterval(30)
                .willPublish()
                .topic(topicPrefix + "connected")
                .qos(MqttQos.EXACTLY_ONCE)
                .retain(true)
                .payload("0".getBytes())
                .contentType("text/plain")
                .messageExpiryInterval(120)
                .delayInterval(30)
                .applyWillPublish()
                .send();

        L.fine("connected " + connAck);

        Map<String, Consumer<Mqtt5Publish>> stringObjectMap = Map.of(
                coreTopicPrefix + Knx2MqttEnum.TOPIC_GROUP_ADDRESSES.getValue() + "/#", this::handleRefreshGaTable
        );
        stringObjectMap.forEach((topic, callback) -> {
            L.info("Successfully connected to broker, subscribing to " + topic);
            mqttc.subscribeWith()
                    .topicFilter(topic)
                    .noLocal(true)
                    .retainHandling(Mqtt5RetainHandling.SEND)
                    .retainAsPublished(true)
                    .callback(callback)
                    .send().join();
        });
        L.info("Subscriptions OK. Threads should be running");
    }

    @Override
    protected String getTopicPrefix() {
        return topicPrefix;
    }

    @Override
    protected byte[] getStatusPayload(KnxStatusDto knxStatusDto) {
        throw new UnsupportedOperationException("Statuses not handled");
    }

    @Override
    protected void handleRefreshGaTable(Mqtt5Publish mqtt5Publish) {
        GroupAddressInfoListDto groupAddressInfoListDto = mqttMessageToGroupAddressInfoDtoListMapper.map(mqtt5Publish);
        if (this.groupAddressInfoListDto.equals(groupAddressInfoListDto)) {
            L.info(String.format("Got update of %s but nothing new", mqtt5Publish.getTopic()));
            return;
        }
        this.groupAddressInfoListDto = groupAddressInfoListDto;
        groupAddressInfoManager.doRefreshGaTable(groupAddressInfoListDto.getGaList());
        String newConfig = new EntitiesFactory().getNewConfig(groupAddressInfoManager.getGaTable());
        mqttc.publishWith()
                .topic(getTopicPrefix() + "new_config")
                .payload(newConfig.getBytes(StandardCharsets.UTF_8))
                .qos(MqttQos.EXACTLY_ONCE)
                .retain(true)
                .send()
                .whenComplete(this::logPublish);
    }
}
