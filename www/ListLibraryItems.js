
var exec = require('cordova/exec');

var listLibraryItems = {
	doSomethingNoArgs: function(onSuccess, onError) {
		console.log('Hello from doSomethingNoArgs');
		cordova.exec(onSuccess, onError, 'ListLibraryItems', 'doSomethingNoArgs', []);
	},

	doSomethingOneArg: function(arg, onSuccess, onError) {
		console.log('Hello from doSomethingOneArg');
		console.log('Called with arg = "' + arg + '"');
		cordova.exec(onSuccess, onError, 'ListLibraryItems', 'doSomethingOneArg', [arg]);
	},

	doSomethingMultipleArgs: function(argA, argB, argC, onSuccess, onError) {
		console.log('Hello from doSomethingMultipleArgs');
		console.log('Called with argA = "' + argA + '"');
		console.log('Called with argB = "' + argB + '"');
		console.log('Called with argC = "' + argC + '"');
		cordova.exec(onSuccess, onError, 'ListLibraryItems', 'doSomethingMultipleArgs', [argA, argB, argC]);
	},

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
	}
};

module.exports = listLibraryItems;