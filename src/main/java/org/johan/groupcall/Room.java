
package org.johan.groupcall;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.PreDestroy;

import org.kurento.client.Continuation;
import org.kurento.client.MediaPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.WebSocketSession;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class Room implements Closeable {
	private final Logger log = LoggerFactory.getLogger(Room.class);

	private final ConcurrentMap<String, UserSession> participants = new ConcurrentHashMap<>();
	private final MediaPipeline pipeline;
	private final String name;

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	public Room(String roomName, MediaPipeline pipeline) {
		this.name = roomName;
		this.pipeline = pipeline;
		log.info("ROOM {} has been created", roomName);
	}

	@PreDestroy
	private void shutdown() {
		this.close();
	}

	public UserSession join(String userName, WebSocketSession session)
			throws IOException {
		log.info("ROOM {}: adding participant {}", name, userName);
		final UserSession participant = new UserSession(userName, this.name,
				session, this.pipeline);
		if (participants.size() <= 0) {
			// First to connect will become admin (visible)
			participant.setAdmin(true);
			participant.setVisible(true);
		} 
		
		joinRoom(participant);
		participants.put(participant.getName(), participant);
		sendParticipantNames(participant);
		return participant;
	}

	public void leave(UserSession user) throws IOException {
		log.debug("PARTICIPANT {}: Leaving room {}", user.getName(), this.name);
		this.removeParticipant(user.getName());
		user.close();
	}

	/**
	 * @param participant
	 * @throws IOException
	 */
	private Collection<String> joinRoom(UserSession newParticipant)
			throws IOException {
		final JsonObject newParticipantMsg = new JsonObject();
		newParticipantMsg.addProperty("id", "newParticipantArrived");
		newParticipantMsg.addProperty("name", newParticipant.getName());
		newParticipantMsg.addProperty("isAdmin", newParticipant.isAdmin());
		newParticipantMsg.addProperty("isVisible", newParticipant.isVisible());

		final List<String> participantsList = new ArrayList<>(participants
				.values().size());
		log.debug(
				"ROOM {}: notifying other participants of new participant {}",
				name, newParticipant.getName());

		for (final UserSession participant : participants.values()) {
			try {
				participant.sendMessage(newParticipantMsg);
			} catch (final IOException e) {
				log.debug("ROOM {}: participant {} could not be notified",
						name, participant.getName(), e);
			}
			participantsList.add(participant.getName());
		}

		return participantsList;
	}

	private void removeParticipant(String name) throws IOException {
		UserSession removedUser = participants.remove(name);
		
		// If admin quits, we have to promote someone else. This is a TODO!
		UserSession newAdminUser = null;
		if (removedUser != null && removedUser.isAdmin()) {
			//newAdminUser = participants.values()
		}

		log.debug("ROOM {}: notifying all users that {} is leaving the room",
				this.name, name);

		final List<String> unnotifiedParticipants = new ArrayList<>();
		final JsonObject participantLeftJson = new JsonObject();
		participantLeftJson.addProperty("id", "participantLeft");
		participantLeftJson.addProperty("name", name);
		for (final UserSession participant : participants.values()) {
			try {
				participant.cancelVideoFrom(name);
				participant.sendMessage(participantLeftJson);
			} catch (final IOException e) {
				unnotifiedParticipants.add(participant.getName());
			}
		}

		if (!unnotifiedParticipants.isEmpty()) {
			log.debug(
					"ROOM {}: The users {} could not be notified that {} left the room",
					this.name, unnotifiedParticipants, name);
		}

	}

	public void sendParticipantNames(UserSession user) throws IOException {

		final JsonArray participantsArray = new JsonArray();
		for (final UserSession participant : this.getParticipants()) {
			if (!participant.equals(user)) {
				JsonObject participantNameAndRights = new JsonObject();

				participantNameAndRights.addProperty("name", participant.getName());
				participantNameAndRights.addProperty("isAdmin", participant.isAdmin());
				participantNameAndRights.addProperty("isVisible", participant.isVisible());		
				
				participantsArray.add(participantNameAndRights);
			}
		}

		final JsonObject existingParticipantsMsg = new JsonObject();
		existingParticipantsMsg.addProperty("id", "existingParticipants");
		existingParticipantsMsg.addProperty("isNewUserAdmin", user.isAdmin());
		existingParticipantsMsg.addProperty("isNewUserVisible", user.isVisible());
		existingParticipantsMsg.add("data", participantsArray);
		log.debug("PARTICIPANT {}: sending a list of {} participants",
				user.getName(), participantsArray.size());
		user.sendMessage(existingParticipantsMsg);
	}

	/**
	 * @return a collection with all the participants in the room
	 */
	public Collection<UserSession> getParticipants() {
		return participants.values();
	}

	/**
	 * @param name
	 * @return the participant from this session
	 */
	public UserSession getParticipant(String name) {
		return participants.get(name);
	}

	@Override
	public void close() {
		for (final UserSession user : participants.values()) {
			try {
				user.close();
			} catch (IOException e) {
				log.debug("ROOM {}: Could not invoke close on participant {}",
						this.name, user.getName(), e);
			}
		}

		participants.clear();

		pipeline.release(new Continuation<Void>() {

			@Override
			public void onSuccess(Void result) throws Exception {
				log.trace("ROOM {}: Released Pipeline", Room.this.name);
			}

			@Override
			public void onError(Throwable cause) throws Exception {
				log.warn("PARTICIPANT {}: Could not release Pipeline",
						Room.this.name);
			}
		});

		log.debug("Room {} closed", this.name);
	}

	/**
	 * Updates the remote(todo, now also current user) visibility of a user. 
	 * @param user
	 */
	public void updateVisibilityFor(UserSession user) {
		user.setVisible(!user.isVisible());
		final JsonObject updateVisibilityMsg = new JsonObject();
		updateVisibilityMsg.addProperty("id", "updateVisibility");
		updateVisibilityMsg.addProperty("user", user.getName());
		updateVisibilityMsg.addProperty("visibility", user.isVisible());
		for (final UserSession participant : participants.values()) {
			try {
				participant.sendMessage(updateVisibilityMsg);
// This somehow also closes the websocket... avoid until clear...
//				if (!user.isVisible()) {
//					participant.cancelVideoFrom(user);					
//				}
			} catch (final IOException e) {
				log.warn(e.getLocalizedMessage());
				e.printStackTrace();
			}
		}
		
	}

}
