// Serve the worker bundle from the worker module's webpack output directory
const path = require('path');

// __dirname is build/js/packages/wdc-cube-kotlin-view-native-web/
// Worker output is at the sibling project's build output
const workerOutputDir = path.resolve(
    __dirname, '..', '..', '..', '..',
    'br.com.wdc.kt.shopping', 'br.com.wdc.kt.shopping.view.native',
    'native.web.worker', 'build', 'kotlin-webpack', 'js', 'developmentExecutable'
);

if (config.devServer) {
    config.devServer.static = config.devServer.static || [];
    if (!Array.isArray(config.devServer.static)) {
        config.devServer.static = [config.devServer.static];
    }
    config.devServer.static.push({
        directory: workerOutputDir,
        publicPath: '/',
    });
}
