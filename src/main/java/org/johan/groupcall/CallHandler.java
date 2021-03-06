package org.johan.groupcall;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

public class CallHandler extends TextWebSocketHandler {

	private static final Logger log = LoggerFactory
			.getLogger(CallHandler.class);

	private static final Gson gson = new GsonBuilder().create();

	@Autowired
	private RoomManager roomManager;

	@Autowired
	private UserRegistry registry;

	/* ****************************************************************************************
	 * FIXME!  In a REALWORLD APP, We have to change name-id to something else, because one can rejoin so fast, 
	 * that the server is still processing the name-to-session registry and get strange results!
	 * ****************************************************************************************
	 */
	
	@Override
	public void handleTextMessage(WebSocketSession session, TextMessage message)
			throws Exception {
		final JsonObject jsonMessage = gson.fromJson(message.getPayload(),
				JsonObject.class);

		final UserSession user = registry.getBySession(session);

		if (user != null) {
			log.debug("Incoming message from user '{}': {}", user.getName(),
					jsonMessage);
		} else {
			log.debug("Incoming message from new user: {}", jsonMessage);
		}

		switch (jsonMessage.get("id").getAsString()) {
		case "joinRoom":
			joinRoom(jsonMessage, session);
			break;
		case "receiveVideoFrom":
			final String senderName = jsonMessage.get("sender").getAsString();
			final UserSession sender = registry.getByName(senderName);
			final String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
			user.receiveVideoFrom(sender, sdpOffer);
			break;
		case "leaveRoom":
			leaveRoom(user);
			break;
		case "changeVisibility":
			changeVisibility(user);
			break;
		case "chat":
			final String text = jsonMessage.get("text").getAsString();
			final String room = jsonMessage.get("room").getAsString();
			sendChatMessage(user, text, room);
			break;
		default:
			break;
		}
	}


	private void sendChatMessage(UserSession chatMsgSender, String text, String roomStr) {
		Room room = roomManager.getRoom(roomStr);
		if (text != null && text.equals("testdisable")) {
			chatMsgSender.getOutgoingWebRtcPeer().getMediaSinks().get(0).release();
		} else {
			room.distributeChatMessage(chatMsgSender, text);			
		}
	}


	@Override
	public void afterConnectionClosed(WebSocketSession session,
			CloseStatus status) throws Exception {
		UserSession user = registry.removeBySession(session);
		if (user != null) {
			roomManager.getRoom(user.getRoomName()).leave(user);
			
		}
	}

	private void joinRoom(JsonObject params, WebSocketSession session)
			throws IOException {
		final String roomName = params.get("room").getAsString();
		final String name = params.get("name").getAsString();
		log.info("PARTICIPANT {}: trying to join room {}", name, roomName);

		Room room = roomManager.getRoom(roomName);
		final UserSession user = room.join(name, session);
		registry.register(user);
	}

	private void leaveRoom(UserSession user) throws IOException {
		final Room room = roomManager.getRoom(user.getRoomName());
		room.leave(user);
		if (room.getParticipants().isEmpty()) {
			roomManager.removeRoom(room);
		}
	}
	

	private void changeVisibility(UserSession user) {
		final Room room = roomManager.getRoom(user.getRoomName());
		room.updateVisibilityFor(user);

	}
}
