// Proxy API and WebSocket requests to the backend server
const backendPort = 8080;

config.devServer = config.devServer || {};
config.devServer.port = 8082;
config.devServer.proxy = [
    {
        context: ['/api'],
        target: `http://localhost:${backendPort}`,
        changeOrigin: true
    }
];
