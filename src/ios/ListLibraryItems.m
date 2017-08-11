
#import "ListLibraryItems.h"

#import <Photos/Photos.h>
#import <MobileCoreServices/MobileCoreServices.h>

static NSString * PERMISSION_ERROR = @"Permission Denial: This application is not allowed to access Photo data.";


@implementation ListLibraryItems
 
- (void)pluginInitialize
{
	// Plugin specific initialize login goes here
}


- (BOOL)checkAuthorization {
    return ([PHPhotoLibrary authorizationStatus] == PHAuthorizationStatusAuthorized);

}


- (void)isAuthorized:(CDVInvokedUrlCommand *)command {
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
        CDVPluginResult * pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsBool:[self checkAuthorization]];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    });
}


- (void)returnUserAuthorization:(BOOL)aAuthorized message:(NSString *)aMessage command:(CDVInvokedUrlCommand *)aCommand {
    CDVPluginResult * pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    if(aAuthorized == NO) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:aMessage];
    }
    [[self commandDelegate] sendPluginResult:pluginResult callbackId:[aCommand callbackId]];
}


- (void)requestReadAuthorization:(CDVInvokedUrlCommand *)command {
    
    PHAuthorizationStatus status = [PHPhotoLibrary authorizationStatus];
    switch (status) {
        case PHAuthorizationStatusAuthorized: {
            [self returnUserAuthorization:YES message:nil command:command];
            
        } break;
        case PHAuthorizationStatusNotDetermined: {
            [PHPhotoLibrary requestAuthorization:^(PHAuthorizationStatus status) {
                if(status == PHAuthorizationStatusAuthorized) {
                    [self returnUserAuthorization:YES message:nil command:command];
                }
                else {
                    [self returnUserAuthorization:NO message:@"requestAuthorization denied by user" command:command];
                }
            }];
        } break;
        case PHAuthorizationStatusDenied: {
            NSURL * url = [NSURL URLWithString:UIApplicationLaunchOptionsURLKey];
            if([[UIApplication sharedApplication] openURL:url]) {
                // TODO: run callback only when return ?
                // Do not call success, as the app will be restarted when user changes permission
            }
            else {
                [self returnUserAuthorization:NO message:@"could not open settings url" command:command];
            }
        } break;
        case PHAuthorizationStatusRestricted: {
            [self returnUserAuthorization:NO message:@"requestAuthorization status restricted" command:command];
        }
    }
}


- (void)listItems:(CDVInvokedUrlCommand *)command {
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
        // check permissions
        if([self checkAuthorization] == NO) {
            CDVPluginResult * pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:PERMISSION_ERROR];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        }
        else {
        
            // parse options
            BOOL includePictures = [[command argumentAtIndex:0 withDefault:[NSNumber numberWithBool:YES]] boolValue];
            BOOL includeVideos = [[command argumentAtIndex:1 withDefault:[NSNumber numberWithBool:YES]] boolValue];
            BOOL includeCloud = [[command argumentAtIndex:2 withDefault:[NSNumber numberWithBool:NO]] boolValue];
            
            // fetch library
            PHFetchOptions * options = [[PHFetchOptions alloc] init];
            [options setIncludeHiddenAssets:NO];
            [options setIncludeAllBurstAssets:NO]; // TODO: add bursts ?
            [options setFetchLimit:0];
            NSSortDescriptor * sort_desc = [NSSortDescriptor sortDescriptorWithKey:@"creationDate" ascending:YES];
            [options setSortDescriptors:@[sort_desc]];
            
            [options setIncludeAssetSourceTypes:PHAssetSourceTypeUserLibrary |
                                                PHAssetSourceTypeiTunesSynced /* TODO: keep itunes stuff ? */ |
                                                (includeCloud ? PHAssetSourceTypeCloudShared : PHAssetSourceTypeNone)];
            
            NSPredicate * predicate = nil;
            if(includePictures == YES && includeVideos == true) {
                predicate = [NSPredicate predicateWithFormat:@"mediaType == %d || mediaType == %d", PHAssetMediaTypeImage, PHAssetMediaTypeVideo];
            }
            else {
                if(includePictures == YES) {
                    predicate = [NSPredicate predicateWithFormat:@"mediaType == %d", PHAssetMediaTypeImage];
                }
                else if(includeVideos == YES) {
                    predicate = [NSPredicate predicateWithFormat:@"mediaType == %d", PHAssetMediaTypeVideo];
                }
            }
            [options setPredicate:predicate];
            
            NSDateFormatter * df = [[NSDateFormatter alloc] init];
            [df setDateFormat:@"yyyy-MM-dd'T'HH:mm:ss.SSSZZZZZ"];

            // loop thru assets and build data
            PHFetchResult * assets = [PHAsset fetchAssetsWithOptions:options];
            NSMutableArray * items = [NSMutableArray array];
            for(PHAsset * asset in assets) {
                
                NSMutableDictionary * item = [NSMutableDictionary dictionary];
                NSString * file_name = [asset valueForKey:@"filename"];
                
                [item setObject:[asset localIdentifier] forKey:@"id"];
                [item setObject:file_name forKey:@"fileName"];
                [item setObject:[NSNumber numberWithUnsignedInteger:[asset pixelWidth]] forKey:@"width"];
                [item setObject:[NSNumber numberWithUnsignedInteger:[asset pixelHeight]] forKey:@"height"];
                [item setObject:[self getMimeTypeFromPath:file_name] forKey:@"mimeType"];
                [item setObject:[df stringFromDate:[asset creationDate]] forKey:@"creationDate"];
                if([asset location]) {
                    [item setObject:[asset localIdentifier] forKey:@"latitude"];
                    [item setObject:[asset localIdentifier] forKey:@"longitude"];
                }
                [items addObject:item];
            }
            
            // send result to js
            NSDictionary * result = [NSDictionary dictionaryWithObjects:@[[NSNumber numberWithUnsignedInteger:[items count]], items]
                                                                forKeys:@[@"count", @"library"]];
            CDVPluginResult * pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:result];
            [[self commandDelegate] sendPluginResult:pluginResult callbackId:[command callbackId]];
        }
    });
}


- (NSString *)getMimeTypeFromPath:(NSString*)fullPath {
    NSString * mimeType = @"application/octet-stream";
    if (fullPath) {
        CFStringRef typeId = UTTypeCreatePreferredIdentifierForTag(kUTTagClassFilenameExtension, (__bridge CFStringRef)[fullPath pathExtension], NULL);
        if (typeId) {
            mimeType = (__bridge_transfer NSString*)UTTypeCopyPreferredTagWithClass(typeId, kUTTagClassMIMEType);
            if (!mimeType) {
                // special case for m4a
                if ([(__bridge NSString*)typeId rangeOfString : @"m4a-audio"].location != NSNotFound) {
                    mimeType = @"audio/mp4";
                } else if ([[fullPath pathExtension] rangeOfString:@"wav"].location != NSNotFound) {
                    mimeType = @"audio/wav";
                } else if ([[fullPath pathExtension] rangeOfString:@"css"].location != NSNotFound) {
                    mimeType = @"text/css";
                }
            }
            CFRelease(typeId);
        }
    }
    return mimeType;
}

@end
