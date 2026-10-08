#import "EventSynthesis.h"
#import <XCTest/XCTest.h>
#import <UIKit/UIKit.h>

// The private classes are reached by name, so a build against an XCTest without them still links
// and +isAvailable reports it.

@protocol JWPointerEventPath <NSObject>
- (instancetype)initForTouchAtPoint:(CGPoint)point offset:(double)offset;
- (instancetype)initForTextInput;
- (void)moveToPoint:(CGPoint)point atOffset:(double)offset;
- (void)liftUpAtOffset:(double)offset;
- (void)typeText:(NSString *)text atOffset:(double)offset typingSpeed:(unsigned long long)speed shouldRedact:(BOOL)redact;
@end

@protocol JWSynthesizedEventRecord <NSObject>
- (instancetype)initWithName:(NSString *)name interfaceOrientation:(long long)orientation;
- (void)addPointerEventPath:(id)path;
@end

@protocol JWEventSynthesizer <NSObject>
- (id)synthesizeEvent:(id)event completion:(void (^)(BOOL result, NSError *error))completion;
@end

/// Characters a second; the rate WebDriverAgent types at by default.
static const unsigned long long TypingSpeed = 60;

/// Longer than the longest gesture or text the host sends.
static const NSTimeInterval SynthesisTimeout = 60;

@implementation EventSynthesis

+ (BOOL)isAvailable {
    return NSClassFromString(@"XCPointerEventPath") != nil
        && NSClassFromString(@"XCSynthesizedEventRecord") != nil
        && [XCUIDevice.sharedDevice respondsToSelector:NSSelectorFromString(@"eventSynthesizer")];
}

+ (id<JWPointerEventPath>)touchAt:(CGPoint)point {
    return [(id<JWPointerEventPath>)[NSClassFromString(@"XCPointerEventPath") alloc] initForTouchAtPoint:point offset:0];
}

+ (nullable NSError *)pressAtPoint:(CGPoint)point duration:(double)duration {
    id<JWPointerEventPath> path = [self touchAt:point];
    [path liftUpAtOffset:duration];
    return [self synthesize:path name:@"press"];
}

+ (nullable NSError *)dragFrom:(CGPoint)from to:(CGPoint)to duration:(double)duration {
    id<JWPointerEventPath> path = [self touchAt:from];
    [path moveToPoint:to atOffset:duration];
    [path liftUpAtOffset:duration + 0.01];
    return [self synthesize:path name:@"drag"];
}

+ (nullable NSError *)typeText:(NSString *)text {
    id<JWPointerEventPath> path = [(id<JWPointerEventPath>)[NSClassFromString(@"XCPointerEventPath") alloc] initForTextInput];
    [path typeText:text atOffset:0 typingSpeed:TypingSpeed shouldRedact:NO];
    return [self synthesize:path name:@"type"];
}

+ (BOOL)pressLockButton {
    SEL selector = NSSelectorFromString(@"pressLockButton");
    if (![XCUIDevice.sharedDevice respondsToSelector:selector]) return NO;
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Warc-performSelector-leaks"
    [XCUIDevice.sharedDevice performSelector:selector];
#pragma clang diagnostic pop
    return YES;
}

// The record's orientation does not change how its points are read: they are device-native
// portrait points in every orientation.
+ (nullable NSError *)synthesize:(id)path name:(NSString *)name {
    id<JWSynthesizedEventRecord> record = [(id<JWSynthesizedEventRecord>)[NSClassFromString(@"XCSynthesizedEventRecord") alloc]
        initWithName:name interfaceOrientation:UIInterfaceOrientationPortrait];
    [record addPointerEventPath:path];
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Warc-performSelector-leaks"
    id<JWEventSynthesizer> synthesizer = [XCUIDevice.sharedDevice performSelector:NSSelectorFromString(@"eventSynthesizer")];
#pragma clang diagnostic pop
    __block NSError *failure = nil;
    __block BOOL finished = NO;
    [synthesizer synthesizeEvent:record completion:^(BOOL result, NSError *error) {
        if (!result) failure = error ?: [NSError errorWithDomain:@"JetWhaleRunner" code:1 userInfo:@{NSLocalizedDescriptionKey: @"XCTest did not synthesize the event"}];
        finished = YES;
    }];
    // Spun rather than blocked: the completion may be delivered on this thread.
    NSDate *deadline = [NSDate dateWithTimeIntervalSinceNow:SynthesisTimeout];
    while (!finished && deadline.timeIntervalSinceNow > 0) {
        [NSRunLoop.currentRunLoop runMode:NSDefaultRunLoopMode beforeDate:[NSDate dateWithTimeIntervalSinceNow:0.005]];
    }
    if (!finished) return [NSError errorWithDomain:@"JetWhaleRunner" code:2 userInfo:@{NSLocalizedDescriptionKey: @"XCTest did not finish the event in time"}];
    return failure;
}

@end

@implementation ExceptionCatcher

+ (nullable NSString *)run:(NS_NOESCAPE void (^)(void))block {
    @try {
        block();
        return nil;
    } @catch (NSException *exception) {
        return [NSString stringWithFormat:@"%@: %@", exception.name, exception.reason];
    }
}

@end
