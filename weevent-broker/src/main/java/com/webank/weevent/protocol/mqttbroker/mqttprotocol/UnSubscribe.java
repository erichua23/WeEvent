package com.webank.weevent.protocol.mqttbroker.mqttprotocol;

import java.util.List;

import com.webank.weevent.broker.plugin.IConsumer;
import com.webank.weevent.protocol.mqttbroker.store.ISubscribeStore;
import com.webank.weevent.sdk.BrokerException;

import io.netty.channel.Channel;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessageFactory;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttUnsubAckMessage;
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

/**
 * @author websterchen
 * @version v1.0
 * @since 2019/6/5
 */
@Slf4j
public class UnSubscribe {
    private ISubscribeStore iSubscribeStore;
    private IConsumer iConsumer;

    public UnSubscribe(ISubscribeStore iSubscribeStore, IConsumer iConsumer) {
        this.iSubscribeStore = iSubscribeStore;
        this.iConsumer = iConsumer;
    }

    public void processUnSubscribe(Channel channel, MqttUnsubscribeMessage msg) {
        List<String> topicFilters = msg.payload().topics();
        String clinetId = (String) channel.attr(AttributeKey.valueOf("clientId")).get();
        topicFilters.forEach(topicFilter -> {
            try {
                iConsumer.unSubscribe(iSubscribeStore.get(topicFilter, clinetId).getSubscriptionId());
            } catch (BrokerException e) {
                log.error("UNSUBSCRIBE error - subscriptionId: {}", iSubscribeStore.get(topicFilter, clinetId).getSubscriptionId());
            }

            iSubscribeStore.remove(topicFilter, clinetId);
            log.debug("UNSUBSCRIBE - clientId: {}, topicFilter: {}", clinetId, topicFilter);
        });
        MqttUnsubAckMessage unsubAckMessage = (MqttUnsubAckMessage) MqttMessageFactory.newMessage(
                new MqttFixedHeader(MqttMessageType.UNSUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                MqttMessageIdVariableHeader.from(msg.variableHeader().messageId()), null);
        channel.writeAndFlush(unsubAckMessage);
    }
}
