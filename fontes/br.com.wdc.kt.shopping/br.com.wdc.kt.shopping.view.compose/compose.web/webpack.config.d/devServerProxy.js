// Proxy API requests to the backend server
// (Overrides the minimal Gradle DSL proxy with full options)
const backendUrl = (typeof process !== 'undefined' && process.env.BASE_URL) || 'http://localhost:8080';

config.devServer = config.devServer || {};

// Always set the full proxy config with proper options
config.devServer.proxy = [
    {
        context: ['/api'],
        target: backendUrl,
        changeOrigin: true,
        secure: false
    }
];
