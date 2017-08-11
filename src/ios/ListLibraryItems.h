// TODO comments, header
#import <Cordova/CDV.h>

@interface ListLibraryItems : CDVPlugin

- (void)pluginInitialize;
//- (void)doSomethingNoArgs:(CDVInvokedUrlCommand*)command;
//- (void)doSomethingOneArg:(CDVInvokedUrlCommand*)command;
//- (void)doSomethingMultipleArgs:(CDVInvokedUrlCommand*)command;
- (void)isAuthorized:(CDVInvokedUrlCommand *)command;
- (void)listItems:(CDVInvokedUrlCommand *)command;
- (void)requestReadAuthorization:(CDVInvokedUrlCommand *)command;
@end
