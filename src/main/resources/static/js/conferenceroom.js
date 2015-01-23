
var ws = new WebSocket('ws://' + location.host + '/groupcall');
var participants = {};
var name; // The local user name... strange that it is in use..
var messageOnOff = false; // Toggling this on off for the messages... not so important :)

window.onbeforeunload = function() {
	ws.close();
};

ws.onmessage = function(message) {
	var parsedMessage = JSON.parse(message.data);
	console.info('Received message: ' + message.data);

	switch (parsedMessage.id) {
	// This is what the new joined user gets (but not the others)
	case 'existingParticipants':
		onExistingParticipants(parsedMessage);
		break;
		// This is what the existing participants get (when the new joins), but not the joiner
	case 'newParticipantArrived':
		onNewParticipant(parsedMessage);
		break;
	case 'participantLeft':
		onParticipantLeft(parsedMessage);
		break;
	case 'receiveVideoAnswer':
		receiveVideoResponse(parsedMessage);
		break;
	case 'updateVisibility':
		updateVisibility(parsedMessage);
		break;
	case 'chatMessageReceived':
		addChatMessage(parsedMessage);
		break;
	default:
		console.error('Unrecognized message', parsedMessage);
	}
}

function register() {
	name = document.getElementById('name').value;
	var room = document.getElementById('roomName').value;

	document.getElementById('room-header').innerText = 'RUM ' + room;
	document.getElementById('join').style.display = 'none';
	document.getElementById('room').style.display = 'block';
	document.getElementById('chat').style.display = 'block';
	


	document.getElementById("write").addEventListener("keydown", function(e) {

	    if (e.keyCode == 13) { // Enter
	    	var str = $( "#write" ).val();
	    	sendTextToOthers(str); 
	    	$( "#write" ).val('');
	    }
	}, false);



	var message = {
		id : 'joinRoom',
		name : name,
		room : room
	}
	sendMessage(message);
	
	addChatMessage({systemMessage: true, text: "Welcome to the chat! Remember to enable your camera if you want to stream video!"})

}

function sendTextToOthers(textStr) {
	if (textStr == null || textStr.length <= 0) {
		return;
	}
	var room = document.getElementById('roomName').value;
	var message = {
			id : 'chat',
			name : name,
			room : room,
			text : textStr
		}
		sendMessage(message);
}

function onNewParticipant(request) {
	// Receive Video from This ONE OTHER that just joined
	receiveVideoAndCreateUser({"name" : request.name, "isVisible" : request.isVisible, "isAdmin" : request.isAdmin});
}

function receiveVideoResponse(result) {
	participants[result.name].rtcPeer.processSdpAnswer(result.sdpAnswer);
}

/** This can also be used internally **/
function addChatMessage(result) {
	var isYou = result.isYou;
	var senderName = result.sender;
	var text = result.text;
	var now = result.time;

	// This one is maybe not provided from the backend (only if it really is a sysmsg)
	var systemMessage = result.systemMessage;
	
	if (senderName != undefined && senderName != null) {
		senderName = '<b>|' + senderName + '| </b> '
	} else {
		senderName = '';
	}
	
	$chatContent = $( "#chatcontent" );
	var $messageLine = $("<div class='usermessage'> " + ((now == undefined || now == null) ? '' : ("[" + now + "]")) + senderName + text + "</div>");
	
	$chatContent.prepend($messageLine);
	
	$messageLine.addClass('usermessage');
	if (messageOnOff) {
		$messageLine.addClass('even');		
	} else {
		$messageLine.addClass('uneven');				
	}
	if (isYou) {
		$messageLine.addClass('you');
	}
	
	if (systemMessage) {
		$messageLine.addClass('systemmessage');
	}
	
	messageOnOff = !messageOnOff;
}

function updateVisibility(result) {
	participants[result.user].updateVisibility(result.visibility);
	participants[result.user].updateInfoSpanText();
	
	// If YOU updated visibility
	if (participants[result.user].isYou) {
		console.info('User (You)' + result.user + ' Updated visibility to ' + result.visibility);
		if (participants[result.user].isVisible) {
			startBroadCastingLocalVideo(participants[result.user]);
		} else {
			stopBroadCastingLocalVideo(participants[result.user]);
		}
	// If SOMEONE ELSE updated visibility
	} else {
		console.info('User (Someone else)' + result.user + ' Updated visibility to ' + result.visibility);

		// Since it is someone else that went visible/not visible, we may want to receive his video stream
		receiveVideoFromExistingUser(participants[result.user]);

	}
	
}

function callResponse(message) {
	if (message.response != 'accepted') {
		console.info('Call not accepted by peer. Closing call');
		stop();
	} else {
		webRtcPeer.processSdpAnswer(message.sdpAnswer);
	}
}

function onExistingParticipants(msg) {
	var constraints = {
		audio : true,
		video : {
			mandatory : {
				maxWidth : 80,
				maxHeight: 80,
				maxFrameRate : 8,
				minFrameRate : 8
			}
		}
	};
	console.log(name + " (you) registered in room " + room);
	var me = new Participant(name, msg.isNewUserAdmin, msg.isNewUserVisible, true);
	participants[name] = me;
	if (me.isVisible) {
		console.log(name + " (you) is visible and starts sending local video");
		startBroadCastingLocalVideo(me);
	} else {
		console.log(name + " (you) is not visible and CANNOT send local video");
	}
	// Receive OTHER VIDEOS to ME (from the array got via msg)
	msg.data.forEach(receiveVideoAndCreateUser);
}

function startBroadCastingLocalVideo(me) {
	var localVideo = me.getVideoElement();
	var constraints = {
			audio : true,
			video : {
				mandatory : {
					maxWidth : 80,
					maxHeight: 80,
					maxFrameRate : 8,
					minFrameRate : 8
				}
			}
		};
	me.rtcPeer = kurentoUtils.WebRtcPeer.startSendOnly(localVideo,
			me.offerToReceiveVideo.bind(me), null,
			constraints);	
}

function stopBroadCastingLocalVideo(me) {
	//me.rtcPeer.dispose();
	console.log(me.rtcPeer.stream.getVideoTracks().length);
	me.rtcPeer.stream.stop();
}

function leaveRoom() {
	console.log('*** leaveRoom() called ******');
	sendMessage({
		id : 'leaveRoom'
	});

	for ( var key in participants) {
		participants[key].dispose();
	}

	document.getElementById('join').style.display = 'block';
	document.getElementById('room').style.display = 'none';

	ws.close();
}

function receiveVideoFromExistingUser(participant) {
	if (participant.isVisible) {
		console.log(participant.name + ' is visible and therefore offering YOU to receive HIS remote video');
		var remoteVideo = participant.getVideoElement();
		participant.rtcPeer = kurentoUtils.WebRtcPeer.startRecvOnly(remoteVideo,
				participant.offerToReceiveVideo.bind(participant));
		
	} else {
		console.log(participant.name + ' is not visible, which means you will not be offered to see HIS remote video');

	}
}

// Here, it is ME receiving videos from the sender(s)
function receiveVideoAndCreateUser(senderNameAndData) {
	var senderName = senderNameAndData.name;
	var isAdmin = senderNameAndData.isAdmin;
	var isVisible = senderNameAndData.isVisible;
	var participant = new Participant(senderName, isAdmin, isVisible, false);
	participants[senderName] = participant;
	receiveVideoFromExistingUser(participant);
}

function onParticipantLeft(request) {
	console.log('Participant ' + request.name + ' left');
	var participant = participants[request.name];
	participant.dispose();
	delete participants[request.name];
}

function sendMessage(message) {
	var jsonMessage = JSON.stringify(message);
	console.log('Senging message: ' + jsonMessage);
	ws.send(jsonMessage);
}
