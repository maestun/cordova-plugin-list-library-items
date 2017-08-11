// TODO comments, header
#import <Cordova/CDV.h>

@interface ListLibraryItems : CDVPlugin <NSURLSessionDelegate, NSURLSessionTaskDelegate, NSURLSessionDataDelegate, NSURLSessionDownloadDelegate, NSURLSessionStreamDelegate>

- (void)pluginInitialize;
- (void)isAuthorized:(CDVInvokedUrlCommand *)command;
- (void)listItems:(CDVInvokedUrlCommand *)command;
- (void)uploadItem:(CDVInvokedUrlCommand *)command;
- (void)requestReadAuthorization:(CDVInvokedUrlCommand *)command;
@end
