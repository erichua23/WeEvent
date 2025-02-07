package com.webank.weevent.sample;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.ConnectionLostException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.RestTemplateXhrTransport;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

/**
 * STOMP client, see
 * https://docs.spring.io/spring/docs/5.0.8.RELEASE/spring-framework-reference/web.html#websocket-stomp
 * https://docs.spring.io/spring/docs/5.0.8.RELEASE/spring-framework-reference/web.html#websocket-stomp-client
 *
 * @author matthewliu
 * @version 1.0
 * @since 2019/02/14
 */
@Slf4j
public class Stomp {
    private final static String brokerStomp = "ws://localhost:8081/weevent/stomp";
    private final static String brokerSockjs = "ws://localhost:8080/weevent/sockjs";
    private final static String topic = "com.webank.test";

    private ThreadPoolTaskScheduler taskScheduler;
    private boolean isConnected;

    public static void main(String[] args) {
        System.out.println("This is WeEvent stomp sample.");

        try {
            Stomp stomp = new Stomp();
            stomp.testOverWebSocket();
            //stomp.testOverSockjs();
        } catch (Exception e) {
            log.error("Exception", e);
        }
    }

    public Stomp() {
        this.taskScheduler = new ThreadPoolTaskScheduler();
        this.taskScheduler.initialize();

        this.isConnected = false;
    }

    private StompSessionHandlerAdapter getWebsocketSessionHandlerAdapter() {
        return new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                log.info("connection open, {}", session.getSessionId());

                session.setAutoReceipt(true);
                // auto subscribe when connected
                log.info("subscribe topic, {}", topic);
                StompHeaders header = new StompHeaders();
                header.set("eventId","2cf24dba-59-1124");
                header.set("groupId","1");
                // extension params
                header.set("weevent-format","json");
                header.setDestination(topic);
                StompSession.Subscription subscription = session.subscribe(header, new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return String.class;
                    }
                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        log.info("subscribe handleFrame, header: {} payload: {}", headers, payload);
                    }
                });
                log.info("subscribe result, subscription id: {}", subscription.getSubscriptionId());

                // subscription.unsubscribe() when needed

                try {
                    Thread.sleep(5000L);
                } catch (InterruptedException e) {
                }

                log.info("send event to topic, {}", topic);
                for (int i = 0; i < 10; i++) {
                    StompSession.Receiptable receiptable = session.send(topic, "hello world, from web socket:" + i);
                    log.info("send result, receipt id: {}", receiptable.getReceiptId());
                }
            }

            @Override
            public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                log.info("connection exception, {} {}", session.getSessionId(), command);
                log.error("exception", exception);
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                if (exception instanceof ConnectionLostException || !isConnected) {
                    log.info("connection closed, {}", session.getSessionId());
                    isConnected = false;
                    // do auto reconnect in this handle
                    while (!isConnected) try {
                        // retry every 3 seconds
                        Thread.sleep(3000);

                        //new connect start
                        WebSocketClient webSocketClient = new StandardWebSocketClient();
                        WebSocketStompClient stompClient = new WebSocketStompClient(webSocketClient);
                        stompClient.setMessageConverter(new StringMessageConverter());
                        stompClient.setTaskScheduler(taskScheduler); // for heartbeats
                        ListenableFuture<StompSession> f = stompClient.connect(brokerStomp, this);
                        f.get();
                        //new connect end

                        isConnected = true;
                    } catch (Exception e) {
                        log.error("exception, {}", exception);
                    }
                } else {
                    log.info("connection error, {}", session.getSessionId());
                    log.error("exception, {}", exception);
                }
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                log.info("session handleFrame, header: {} payload: {}", headers, payload);
            }
        };
    }

    private void testOverWebSocket() throws InterruptedException {
        // standard web socket transport
        WebSocketClient webSocketClient = new StandardWebSocketClient();
        WebSocketStompClient stompClient = new WebSocketStompClient(webSocketClient);

        // MappingJackson2MessageConverter
        stompClient.setMessageConverter(new StringMessageConverter());
        stompClient.setTaskScheduler(taskScheduler); // for heartbeats

        stompClient.connect(brokerStomp, getWebsocketSessionHandlerAdapter());

        Thread.sleep(100000L);
    }

    private StompSessionHandlerAdapter getSockjsSessionHandlerAdapter() {
        return new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                log.info("connection open, {}", session.getSessionId());

                session.setAutoReceipt(true);
                // auto subscribe when connected
                StompHeaders header = new StompHeaders();
                header.set("eventId","2cf24dba-59-1124");
                header.set("groupId","1");
                // extension params
                header.set("weevent-format","json");
                header.setDestination(topic);
                StompSession.Subscription subscription = session.subscribe(header, new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return String.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        log.info("subscribe handleFrame, header: {} payload: {}", headers, payload);
                    }
                });
                log.info("subscribe result, subscription id: {}", subscription.getSubscriptionId());

                // subscription.unsubscribe() when needed

                try {
                    Thread.sleep(5000L);
                } catch (InterruptedException e) {
                }
                StompHeaders header = new StompHeaders();
                header.setDestination(topic);
                log.info("send event to topic, {}", topic);
                for (int i = 0; i < 10; i++) {
                    StompSession.Receiptable receiptable = session.send(topic, "hello world, from sock js:" + i);
                    log.info("send result, receipt id: {}", receiptable.getReceiptId());
                }
            }

            @Override
            public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                log.info("connection exception, {} {}", session.getSessionId(), command);
                log.error("exception, {}", exception);
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                if (exception instanceof ConnectionLostException) {
                    log.info("connection closed, {}", session.getSessionId());

                    // do auto reconnect in this handle
                    while (!isConnected) try {
                        // retry every 3 seconds
                        Thread.sleep(3000);

                        //new connect start
                        // sock js transport
                        List<Transport> transports = new ArrayList<>(2);
                        transports.add(new WebSocketTransport(new StandardWebSocketClient()));
                        transports.add(new RestTemplateXhrTransport());

                        SockJsClient sockjsClient = new SockJsClient(transports);
                        WebSocketStompClient stompClient = new WebSocketStompClient(sockjsClient);

                        // StringMessageConverter
                        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
                        stompClient.setTaskScheduler(taskScheduler); // for heartbeats
                        ListenableFuture<StompSession> f = stompClient.connect(brokerSockjs, this);
                        f.get();
                        //new connect end

                        isConnected = true;
                    } catch (Exception e) {
                        log.error("exception, {}", exception);
                    }
                } else {
                    log.info("connection error, {}", session.getSessionId());
                    log.error("exception, {}", exception);
                }
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                log.info("session handleFrame, header: {} payload: {}", headers, payload);
            }
        };
    }

    private void testOverSockjs() throws InterruptedException {
        // sock js transport
        List<Transport> transports = new ArrayList<>(2);
        transports.add(new WebSocketTransport(new StandardWebSocketClient()));
        transports.add(new RestTemplateXhrTransport());

        SockJsClient sockjsClient = new SockJsClient(transports);
        WebSocketStompClient stompClient = new WebSocketStompClient(sockjsClient);

        // StringMessageConverter
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        stompClient.setTaskScheduler(taskScheduler); // for heartbeats

        stompClient.connect(brokerSockjs, getSockjsSessionHandlerAdapter());

        Thread.sleep(100000L);
    }
}
