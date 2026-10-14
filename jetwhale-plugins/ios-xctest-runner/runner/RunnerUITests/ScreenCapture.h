#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// Screenshots through testmanagerd's private `_XCT_requestScreenshot:withReply:`, as WebDriverAgent's
/// MJPEG server takes them: the daemon encodes them as JPEG, and the request may be sent from any
/// thread. The public `XCUIScreen` screenshot takes about five times as long and holds the main thread.
@interface ScreenCapture : NSObject
/// Whether this XCTest has the private classes the request needs.
+ (BOOL)isAvailable;
/// The main screen's display ID, which a request names. Call it on the main thread.
+ (long long)mainScreenID;
+ (void)requestJpegOfScreen:(long long)screenID quality:(double)quality completion:(void (^)(NSData *_Nullable data, NSError *_Nullable error))completion;
@end

NS_ASSUME_NONNULL_END
