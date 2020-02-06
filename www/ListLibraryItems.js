
var exec = require('cordova/exec');

var listLibraryItems = {

	isAuthorized: function(onSuccess, onError) {
		console.log('ListLibraryItems plugin: calling isAuthorized...');
		cordova.exec(onSuccess, onError, 'ListLibraryItems', 'isAuthorized', []);
	},

	requestReadAuthorization: function(onSuccess, onError, options) {
		console.log('ListLibraryItems plugin: calling requestAuthorization...');
		cordova.exec(onSuccess, onError, 'ListLibraryItems', 'requestReadAuthorization', []);
	},

	listItems: function(includePictures, includeVideos, includeCloud, onSuccess, onError) {
		console.log('ListLibraryItems plugin: calling listItems...');
		cordova.exec(onSuccess, onError, 'ListLibraryItems', 'listItems', [includePictures, includeVideos, includeCloud]);
	},

	uploadItem: function(payload, onSuccess, onError) {
		console.log('ListLibraryItems plugin: calling uploadItem...');
		cordova.exec(onSuccess, onError, 'ListLibraryItems', 'uploadItem', [payload]);
	}
};

module.exports = listLibraryItems;