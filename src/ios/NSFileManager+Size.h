#import <Foundation/Foundation.h>

@interface NSFileManager (Size)

- (NSNumber*)sizeOfItemAtURL:(nonnull NSURL*)fileURL;

@end
