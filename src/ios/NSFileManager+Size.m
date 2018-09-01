#import "NSFileManager+Size.h"

@implementation NSFileManager (Size)

- (NSNumber*)sizeOfItemAtURL:(nonnull NSURL*)fileURL {
    NSString * filePath = [fileURL path];
    NSDictionary * attributes = [self attributesOfItemAtPath:filePath error:nil];
    return [attributes objectForKey:NSFileSize];
}

@end
