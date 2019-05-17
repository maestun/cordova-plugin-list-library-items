
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

	listItems: function(includePictures, includeVideos, includeCloud, mediaBuckets, onSuccess, onError) {
		console.log('ListLibraryItems plugin: calling listItems for buckets [%s]...', [...mediaBuckets].join(', '));
		cordova.exec(onSuccess, onError, 'ListLibraryItems', 'listItems', [includePictures, includeVideos, includeCloud, mediaBuckets]);
	},

	uploadItem: function(payload, onSuccess, onError) {
		console.log('ListLibraryItems plugin: calling uploadItem...');
		cordova.exec(onSuccess, onError, 'ListLibraryItems', 'uploadItem', [payload]);
	},

	listMediaBuckets: function(onSuccess, onError) {
		console.log('ListLibraryItems plugin: calling listMediaBuckets...');
		cordova.exec(onSuccess, onError, 'ListLibraryItems', 'listMediaBuckets', []);
	}
};

module.exports = listLibraryItems;
