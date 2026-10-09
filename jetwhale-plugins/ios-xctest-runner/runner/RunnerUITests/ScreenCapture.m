#import "ScreenCapture.h"
#import <XCTest/XCTest.h>
#import <CoreGraphics/CoreGraphics.h>

// The private classes are reached by name, so a build against an XCTest without them still links
// and +isAvailable reports it.

@protocol JWRunnerDaemonSession <NSObject>
+ (instancetype)sharedSession;
- (id)daemonProxy;
@end

@protocol JWScreenshotDaemon <NSObject>
- (void)_XCT_requestScreenshot:(id)request withReply:(void (^)(id image, NSError *error))reply;
@end

@protocol JWImageEncoding <NSObject>
- (instancetype)initWithUniformTypeIdentifier:(NSString *)uti compressionQuality:(double)quality;
@end

@protocol JWScreenshotRequest <NSObject>
- (instancetype)initWithScreenID:(long long)screenID rect:(CGRect)rect encoding:(id)encoding;
@end

@protocol JWImage <NSObject>
- (NSData *)data;
@end

@protocol JWScreen <NSObject>
- (long long)displayID;
@end

@implementation ScreenCapture

+ (BOOL)isAvailable {
    Class session = NSClassFromString(@"XCTRunnerDaemonSession");
    if (![session respondsToSelector:@selector(sharedSession)] || ![session instancesRespondToSelector:@selector(daemonProxy)]) return NO;
    // A message the daemon's interface lacks raises an exception on the stream's queue, which
    // nothing there catches.
    id<JWRunnerDaemonSession> daemonSession = [(Class<JWRunnerDaemonSession>)session sharedSession];
    id proxy = daemonSession.daemonProxy;
    return [proxy respondsToSelector:@selector(_XCT_requestScreenshot:withReply:)]
        && [NSClassFromString(@"XCTScreenshotRequest") instancesRespondToSelector:@selector(initWithScreenID:rect:encoding:)]
        && [NSClassFromString(@"XCTImageEncoding") instancesRespondToSelector:@selector(initWithUniformTypeIdentifier:compressionQuality:)]
        && [XCUIScreen.mainScreen respondsToSelector:@selector(displayID)];
}

+ (long long)mainScreenID {
    return [(id<JWScreen>)XCUIScreen.mainScreen displayID];
}

+ (void)requestJpegOfScreen:(long long)screenID quality:(double)quality completion:(void (^)(NSData *, NSError *))completion {
    id<JWRunnerDaemonSession> session = [(Class<JWRunnerDaemonSession>)NSClassFromString(@"XCTRunnerDaemonSession") sharedSession];
    id encoding = [(id<JWImageEncoding>)[NSClassFromString(@"XCTImageEncoding") alloc] initWithUniformTypeIdentifier:@"public.jpeg" compressionQuality:quality];
    id request = [(id<JWScreenshotRequest>)[NSClassFromString(@"XCTScreenshotRequest") alloc] initWithScreenID:screenID rect:CGRectNull encoding:encoding];
    [(id<JWScreenshotDaemon>)session.daemonProxy _XCT_requestScreenshot:request withReply:^(id image, NSError *error) {
        completion([image respondsToSelector:@selector(data)] ? [(id<JWImage>)image data] : nil, error);
    }];
}

@end
