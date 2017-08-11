
#import "ListLibraryItems.h"

#import <Photos/Photos.h>
#import <MobileCoreServices/MobileCoreServices.h>

#import "AFURLSessionManager.h"

static NSString * PERMISSION_ERROR = @"Permission Denial: This application is not allowed to access Photo data.";


@implementation ListLibraryItems
 
- (void)pluginInitialize {
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


- (void)returnUploadResult:(BOOL)aAuthorized message:(NSString *)aMessage command:(CDVInvokedUrlCommand *)aCommand {
    CDVPluginResult * pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    if(aAuthorized == NO) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:aMessage];
    }
    [[self commandDelegate] sendPluginResult:pluginResult callbackId:[aCommand callbackId]];
}


- (void)uploadItem:(CDVInvokedUrlCommand *)command {
    /*
     var payload = {
     "id": "sj5f9"
     "filePath": "/storage/emulated/0/Download/Heli.divx",
     "serverUrl": "http://requestb.in/14cizzj1",
     "headers": {
     "api_key": "asdasdwere123sad"
     },
     "parameters": {
     "signature": "mysign",
     "timestamp": 112321321
     }
     };
     
     uploader.startUpload(options);
    */
    NSDictionary * payload = command.arguments[0];
    NSString * uploadUrl  = payload[@"serverUrl"];
//    NSString * filePath  = payload[@"filePath"];
    NSDictionary * headers = payload[@"headers"];
//    NSDictionary * parameters = payload[@"parameters"];
    NSString * libraryId = payload[@"libraryId"];
    NSString * fileId = payload[@"id"];
    
    
    // try to fetch asset
    PHFetchResult<PHAsset *> * assets = [PHAsset fetchAssetsWithLocalIdentifiers:@[libraryId] options:kNilOptions];
    if([assets count] == 0) {
        // asset id not found
        [self returnUploadResult:NO message:[NSString stringWithFormat:@"Cannot fetch asset %@", libraryId] command:command];
    }
    else {
        for(PHAsset * asset in assets) {
            PHAssetResource * resource = [[PHAssetResource assetResourcesForAsset:asset] firstObject];;
            NSString * temp_path = [NSTemporaryDirectory() stringByAppendingString:[resource originalFilename]];
            NSURL * temp_url = [NSURL fileURLWithPath:temp_path];
            [[NSFileManager defaultManager] removeItemAtURL:temp_url error:nil]; // cleanup
            PHAssetResourceRequestOptions * options = [PHAssetResourceRequestOptions new];
            [options setNetworkAccessAllowed: NO];
            [options setProgressHandler:^(double progress) {
                NSLog(@"progress %f", progress);
            }];
            
            [[PHAssetResourceManager defaultManager] writeDataForAssetResource:resource toFile:temp_url options:options completionHandler:^(NSError * _Nullable aError) {
                if (aError) {
                    [self returnUploadResult:NO message:[NSString stringWithFormat:@"Cannot fetch asset %@", libraryId] command:command];
                }
                else {
                    NSError * err = nil;
                    NSString * mime_type = [headers objectForKey:@"Content-Type"];
                    NSMutableURLRequest * request =  [[AFHTTPRequestSerializer serializer] multipartFormRequestWithMethod:@"POST"
                                                                                                                URLString:uploadUrl
                                                                                                               parameters:nil
                                                                                                constructingBodyWithBlock:^(id<AFMultipartFormData> formData) {
                                                                                                    NSError * error = nil;
                                                                                                    [formData appendPartWithFileURL:temp_url name:fileId error:&error];
                                                                                                    if(error) {
                                                                                                        NSLog(@"%@", error);
                                                                                                    }
                                                                                                }
                                                                                                                    error:&err];
                    
                    
                    request = [[AFHTTPRequestSerializer serializer] multipartFormRequestWithMethod:@"POST"
                                                                                         URLString:uploadUrl
                                                                                        parameters:nil
                                                                         constructingBodyWithBlock:^(id<AFMultipartFormData>  _Nonnull formData) {
                        [formData appendPartWithFileURL:temp_url name:fileId fileName:fileId mimeType:mime_type error:nil];
                    }
                                                                                             error:nil];
                    
//                {
//                        
//                    NSURLSessionConfiguration * config = [NSURLSessionConfiguration defaultSessionConfiguration];
//                    NSURLSession * defaultSession = [NSURLSession sessionWithConfiguration:config delegate:self delegateQueue:[NSOperationQueue mainQueue]];
//
//                    NSMutableURLRequest *mutableRequest = [NSMutableURLRequest requestWithURL:[NSURL URLWithString:uploadUrl]];
//                    mutableRequest.HTTPMethod = @"POST";
//                    
//                    for(NSString * header in [headers allKeys]) {
//                        [mutableRequest setValue:[headers objectForKey:header] forHTTPHeaderField:header];
//                    }
//
//                    NSURLSessionUploadTask *uploadTask = [defaultSession uploadTaskWithRequest:mutableRequest fromFile:temp_url];
//                    [uploadTask resume];
//                    return;
//                }

                
                    
//                    NSMutableURLRequest * request = [[AFHTTPRequestSerializer serializer] requestWithMethod:@"POST" URLString:uploadUrl parameters:nil error:nil];
//                    [request setHTTPMethod:@"POST"];
//                    [request setHTTPBodyStream:[NSInputStream inputStreamWithURL:temp_url]];
                    for(NSString * header in [headers allKeys]) {
                        [request setValue:[headers objectForKey:header] forHTTPHeaderField:header];
                    }
//
//                    NSURLSessionConfiguration * config = [NSURLSessionConfiguration defaultSessionConfiguration];
//                    
//                    NSURLSession * session = [NSURLSession sessionWithConfiguration:config delegate:self delegateQueue: [NSOperationQueue mainQueue]];
//                    NSURLSessionUploadTask * task = [session uploadTaskWithStreamedRequest:request];
//                    
//                    [task resume];
//
//                    return;

                    
                    
                    AFURLSessionManager * manager = [[AFURLSessionManager alloc] initWithSessionConfiguration:[NSURLSessionConfiguration defaultSessionConfiguration]];
                    NSURLSessionUploadTask * uploadTask = [manager uploadTaskWithStreamedRequest:request progress:^(NSProgress * _Nonnull uploadProgress) {
                        // call js every 1 %
                        static double prev_progress = 0;
                        if([uploadProgress fractionCompleted] - prev_progress > 0.01) {
                            prev_progress = [uploadProgress fractionCompleted];
                            // TODO: call onProgress
                        }
                        NSLog(@"%@", uploadProgress);
                    }
                    completionHandler:^(NSURLResponse * _Nonnull response, id  _Nullable responseObject, NSError * _Nullable aError) {
                        NSHTTPURLResponse * httpResponse = (NSHTTPURLResponse *) response;
                        long status = (long)[httpResponse statusCode];
                        
                        [[NSFileManager defaultManager] removeItemAtURL:temp_url error:nil];
                        if (aError && status >= 400) {
                          NSLog(@"Error: %@", aError);
                          [self returnUploadResult:NO message:[NSString stringWithFormat:@"upload error: %@", [aError localizedDescription]] command:command];
                      } else {
                          NSLog(@"%@ %@", response, responseObject);
                          [self returnUploadResult:YES message:[NSString stringWithFormat:@"upload error: %@", [aError localizedDescription]] command:command];
                      }
                    }];
                                
                    [uploadTask resume];
                }
            }];
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


#pragma mark - NSURLSessionDelegate
- (void)URLSession:(NSURLSession *)session didBecomeInvalidWithError:(NSError *)error {
    NSLog(@"didBecomeInvalidWithError");
}

- (void)URLSession:(NSURLSession *)session didReceiveChallenge:(NSURLAuthenticationChallenge *)challenge completionHandler:(void (^)(NSURLSessionAuthChallengeDisposition, NSURLCredential * _Nullable))completionHandler {
    NSLog(@"didReceiveChallenge");
}

- (void)URLSessionDidFinishEventsForBackgroundURLSession:(NSURLSession *)session {
 NSLog(@"URLSessionDidFinishEventsForBackgroundURLSession");
}

#pragma mark - NSURLSessionTaskDelegate
- (void)URLSession:(NSURLSession *)session task:(NSURLSessionTask *)task didSendBodyData:(int64_t)bytesSent totalBytesSent:(int64_t)totalBytesSent totalBytesExpectedToSend:(int64_t)totalBytesExpectedToSend {
 NSLog(@"didSendBodyData");
}

- (void)URLSession:(NSURLSession *)session task:(NSURLSessionTask *)task didCompleteWithError:(NSError *)error {
 NSLog(@"didCompleteWithError");
}
- (void)URLSession:(NSURLSession *)session task:(NSURLSessionTask *)task needNewBodyStream:(nonnull void (^)(NSInputStream * _Nullable))completionHandler {
 NSLog(@"needNewBodyStream");
}

@end
