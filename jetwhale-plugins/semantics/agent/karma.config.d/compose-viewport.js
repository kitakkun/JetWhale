// The web probe tests render a ComposeViewport and wait for Compose to write its accessibility DOM,
// which takes longer than Mocha's default two seconds.
config.set({
    client: {
        mocha: {
            timeout: 60000
        }
    }
});

// Skia draws through WebGL, which headless Chrome on a machine without a GPU (a CI runner) only
// offers through SwiftShader, and only when asked to.
config.set({
    browsers: ['ChromeHeadlessWithWebGL'],
    customLaunchers: {
        ChromeHeadlessWithWebGL: {
            base: 'ChromeHeadless',
            flags: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader']
        }
    }
});
