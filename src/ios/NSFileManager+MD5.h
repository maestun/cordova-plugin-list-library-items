#import <Foundation/Foundation.h>

@interface NSFileManager (MD5)

- (NSString*)md5OfItemAtURL:(nonnull NSURL*)fileURL;

@end
