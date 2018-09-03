#import "NSData+MD5.h"
#import <CommonCrypto/CommonDigest.h>

@implementation NSData(MD5)

- (NSData*)MD5 {
    unsigned char md5Buffer[CC_MD5_DIGEST_LENGTH];
    CC_MD5(self.bytes, (CC_LONG)self.length, md5Buffer);

    return [[NSData alloc] initWithBytes:md5Buffer length:CC_MD5_DIGEST_LENGTH];
}

@end
