// The conformance runner plays a host over a fake socket, with real waits (the handshake's Web Crypto, a quiet
// half second): more than Mocha's default 2 s per test.
config.set({
  client: {
    mocha: {
      timeout: 30000,
    },
  },
})
