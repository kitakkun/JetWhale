#import <Foundation/Foundation.h>
#import <CoreGraphics/CoreGraphics.h>

NS_ASSUME_NONNULL_BEGIN

/// Touches, text and the lock button through XCTest's private event synthesis, as WebDriverAgent
/// sends them. Unlike XCUICoordinate it does not wait for the app to idle, and it takes
/// device-native portrait points.
@interface EventSynthesis : NSObject
+ (BOOL)isAvailable;
+ (nullable NSError *)pressAtPoint:(CGPoint)point duration:(double)duration;
+ (nullable NSError *)dragFrom:(CGPoint)from to:(CGPoint)to duration:(double)duration;
+ (nullable NSError *)typeText:(NSString *)text;
+ (BOOL)pressLockButton;
@end

@interface ExceptionCatcher : NSObject
/// Runs [block] and returns the reason of an Objective-C exception it raised, or nil.
+ (nullable NSString *)run:(NS_NOESCAPE void (^)(void))block;
@end

NS_ASSUME_NONNULL_END
