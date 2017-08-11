
var exec = require('cordova/exec');

var listLibraryItems = {

	isAuthorized: function(onSuccess, onError) {
		console.log('Hello from isAuthorized');
		cordova.exec(onSuccess, onError, 'ListLibraryItems', 'isAuthorized', []);
	},

	requestReadAuthorization: function(onSuccess, onError, options) {
		console.log('Hello from requestAuthorization');
		cordova.exec(onSuccess, onError, 'ListLibraryItems', 'requestReadAuthorization', []);
	},

	listItems: function(includePictures, includeVideos, includeCloud, onSuccess, onError) {
		console.log('Hello from listItems');
		cordova.exec(onSuccess, onError, 'ListLibraryItems', 'listItems', [includePictures, includeVideos, includeCloud]);
	},

	uploadItem: function(payload, onSuccess, onError) {
		console.log('Hello from uploadItem');
		cordova.exec(onSuccess, onError, 'ListLibraryItems', 'uploadItem', [payload]);
	}
};

module.exports = listLibraryItems;