const dotenv = require('dotenv');
const HttpsProxyAgent = require('https-proxy-agent');
dotenv.config();

const proxyConfig = [
  {
    context: ['/'],
    target: 'http://localhost:8080',
    secure: false,
    changeOrigin: true
  },
  {
    context: '/ws',
    target: 'ws://localhost:8080',
    secure: false,
    ws:true,
    changeOrigin: true
  }
];

function httpUrlToWSUrl(url) {
  return url.replace(/(http)(s)?\:\/\//, "ws$2://");
}

function setupForCorporateProxy(proxyConfig) {
  const proxyServer = process.env.SERVER_PROXY;
  const httpProxy = process.env.HTTP_PROXY;
  if (proxyServer) {
    let agent = null;
    if (httpProxy) {
      agent = new HttpsProxyAgent(httpProxy);
    }
    proxyConfig.forEach(function(entry) {
      if (entry.context === '/ws') {
        entry.target = httpUrlToWSUrl(proxyServer)
      } else {
        entry.target = proxyServer;
      }
      if (agent) {
        entry.agent = agent;
      }
    });
  }
  return proxyConfig;
}

module.exports = setupForCorporateProxy(proxyConfig);
