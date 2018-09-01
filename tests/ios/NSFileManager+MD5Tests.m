#import <XCTest/XCTest.h>
#import "../../src/ios/NSFileManager+MD5.h"

@interface NSFileManager_MD5Tests : XCTestCase

@end

@implementation NSFileManager_MD5Tests

- (void)test_should_return_MD5_as_base64_encoded {
    // Given
    NSURL * fileURL = [[NSBundle bundleForClass:[NSFileManager_MD5Tests class]] URLForResource:@"sample" withExtension:@"txt"];

    // When
    NSString * fileMD5 = [[NSFileManager defaultManager] md5OfItemAtURL:fileURL];

    // Then
    XCTAssertEqualObjects(fileMD5, @"sFUPnIpOFZ0/aM7qtSCynA==");
}

@end
