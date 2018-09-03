#import "NSFileManager+MD5.h"
#import "NSData+MD5.h"

@implementation NSFileManager (MD5)

- (NSString*)md5OfItemAtURL:(nonnull NSURL*)fileURL {
    NSData * fileContent = [NSData dataWithContentsOfURL:fileURL];
    NSData * md5 = [fileContent MD5];
    return [md5 base64EncodedStringWithOptions:NSDataBase64EncodingEndLineWithLineFeed];
}

@end
