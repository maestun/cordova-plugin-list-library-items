#import <XCTest/XCTest.h>
#import "../../src/ios/NSFileManager+Size.h"

@interface NSFileManager_SizeTests : XCTestCase

@end

@implementation NSFileManager_SizeTests

- (void)test_should_return_file_size {
    // Given
    NSURL * fileURL = [[NSBundle bundleForClass:[NSFileManager_SizeTests class]] URLForResource:@"sample" withExtension:@"txt"];

    // When
    NSNumber * fileSize = [[NSFileManager defaultManager] sizeOfItemAtURL:fileURL];

    // Then
    XCTAssertEqualObjects(fileSize, @1048576);
}

@end
