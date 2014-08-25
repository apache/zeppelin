function Zeppelin(serverAddr){
  this.ws = new WebSocket(serverAddr);
  this.ws.onmessage = function(msg) {
    console.log(msg);
  };
  
  this.ws.onOpen = function(response) {
    console.log("Websocket created %o", response);
  };

  this.ws.onError = function(response){
    console.log("On error %o", response);
  };

/*
    this.serverAddr = serverAddr;
    this.socket = $.atmosphere;

    this.request = {
        url: serverAddr,
        contentType : "application/json",
        logLevel : 'debug',
        transport : 'websocket',
        fallbackTransport: 'long-polling'
    };
    
    this.request.onOpen = function(response) {
        console.log("Websocket created %o", response);
    };

    this.request.onMessage = function(response) {
        console.log("On message %o", response);
    };

    this.request.onError = function(response){
        console.log("On error %o", response);
    };

    var subSocket = this.socket.subscribe(this.request);
*/
};

function Note(id){
    this.id = id;

    this.render = function(target){
    }
};


function Pargraph(id){
    this.id = id;
    
    render = function(target){
    }
};


var zp = new Zeppelin("ws://localhost:8081");
console.log(">>>>>>>>> READY <<<<<<<<<<<");
