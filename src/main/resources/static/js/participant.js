const PARTICIPANT_MAIN_CLASS = 'participant main';
const PARTICIPANT_CLASS = 'participant';

/**
 * Creates a video element for a new participant
 *
 * @param {String} name - the name of the new participant, to be used as tag
 *                        name of the video element.
 *                        The tag of the new element will be 'video<name>'
 * @return
 */
/**
 */
function Participant(name, isAdmin, isVisible, isYou) {
	this.name = name;
	this.isAdmin = isAdmin;
	this.isYou = isYou;
	this.isVisible = isVisible;
	var container = document.createElement('div');
	container.className = isPresentMainParticipant() ? PARTICIPANT_CLASS : PARTICIPANT_MAIN_CLASS;
	container.id = name;
	var span = document.createElement('span');
	this.$infoSpan =  $('<span />');
	var video = document.createElement('video');
	var rtcPeer;
	
	var self = this;

	container.appendChild(video);
	container.appendChild(span);
	container.appendChild(this.$infoSpan[0]);
	container.onclick = switchContainerClass;
	document.getElementById('participants').appendChild(container);

	span.appendChild(document.createTextNode(name));

	video.id = 'video-' + name;
	video.autoplay = true;
	video.controls = false;


	this.getElement = function() {
		return container;
	}

	this.getVideoElement = function() {
		return video;
	}

	function switchContainerClass() {
		
		// At the moment, you can only change your own visibility
		if (self.isYou) {
			// This will tell the application, that we changed visibility
			sendMessage({
				id : 'changeVisibility',
				name : self.name, // Is this even needed
			});

			
		}
		
		if (container.className === PARTICIPANT_CLASS) {
			var elements = Array.prototype.slice.call(document.getElementsByClassName(PARTICIPANT_MAIN_CLASS));
			elements.forEach(function(item) {
					item.className = PARTICIPANT_CLASS;
				});

				container.className = PARTICIPANT_MAIN_CLASS;
			} else {
			container.className = PARTICIPANT_CLASS;
		}
	}
	
	
	function isPresentMainParticipant() {
		return ((document.getElementsByClassName(PARTICIPANT_MAIN_CLASS)).length != 0);
	}
	
	this.updateVisibility = function(isVisible) {
		self.isVisible = isVisible;
	}

	this.updateInfoSpanText = function() {
		var str = this.isYou ? ' (You!)' : '';
		str = str + (this.isAdmin ? ' (Admin)' : '');
		str = str + (this.isVisible ? ' (Visible)' : '(Not visible)');
		self.$infoSpan.text(str);
	}


	this.offerToReceiveVideo = function(offerSdp, wp){
		console.log('Invoking SDP offer callback function');
		console.log(name + " Sends offer message to receive video");
		var msg =  { id : "receiveVideoFrom",
				sender : name,
				sdpOffer : offerSdp
			};
		sendMessage(msg);
	}

	Object.defineProperty(this, 'rtcPeer', { writable: true});

	this.dispose = function() {
		console.log('Disposing participant ' + this.name);
		if (this.rtcPeer != undefined && this.rtcPeer != null) {
			this.rtcPeer.dispose();			
		}
		container.parentNode.removeChild(container);
	};
	
	this.updateInfoSpanText();
}
