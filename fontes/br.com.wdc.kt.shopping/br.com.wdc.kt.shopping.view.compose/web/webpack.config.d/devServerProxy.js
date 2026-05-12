// Proxy API and WebSocket requests to the backend server
const backendUrl = (typeof process !== 'undefined' && process.env.BASE_URL) || 'http://localhost:8080';

config.devServer = config.devServer || {};
config.devServer.port = 8082;
config.devServer.proxy = [
    {
        context: ['/api'],
        target: backendUrl,
        changeOrigin: true
    }
];
