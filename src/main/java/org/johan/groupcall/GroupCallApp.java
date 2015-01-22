package org.johan.groupcall;

import org.kurento.client.factory.KurentoClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;


@Configuration
@EnableWebSocket
@EnableAutoConfiguration
public class GroupCallApp implements WebSocketConfigurer {

	@Bean
	public UserRegistry registry() {
		return new UserRegistry();
	}

	@Bean
	public RoomManager roomManager() {
		return new RoomManager();
	}

	@Bean
	public CallHandler groupCallHandler() {
		return new CallHandler();
	}

	@Bean
	public static KurentoClient kurentoClient() {
		
		// Bridged solution (IP for Ubuntu64New) also under name: johaniv
		return KurentoClient.create("ws://130.232.86.113:8888/kurento");
		
		// Johans machine, port forwarding via own address
		//return KurentoClient.create("ws://130.232.86.111:8888/kurento");
		
			//ubuntu behind a NAT
			//return KurentoClient.create("ws://192.168.56.1:8888/kurento");

			// mac (samimacpro)
			//return KurentoClient.create("ws://130.232.84.66:8888/kurento");
	}
	
// This is just testing
//	public static KurentoClient kurentoClient() {
//	//own comp	
//		if (test <= 0) {
//			test++;
//			return KurentoClient.create("ws://192.168.56.1:8888/kurento");
//			
//		} else {
//			return KurentoClient.create("ws://130.232.84.66:8888/kurento");
//		}
//		// mac (samimacpro)
//	}

	public static void main(String[] args) throws Exception {
		SpringApplication.run(GroupCallApp.class, args);
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(groupCallHandler(), "/groupcall");
	}
}
