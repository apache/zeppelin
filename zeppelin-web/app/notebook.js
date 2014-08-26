function Notebook(config){
    var notebook = this;
    this.config = config;

    this.ws = new WebSocket(config.socket);

    // current note
    this.currentNote;

    this.setNote = function(note){
        console.log("setNote %o", note);
        var target = $(config.target.selector);

        if(this.currentNote) {
            if(this.currentNote.data.id == note.data.id){
                // update current note
            } else {
                // change to another note
                this.currentNote.distroy();
                this.currentNote = new Note(note)
                currentNote.render(target)
            }
        } else {
            // load note
            this.currentNote = new Note(note)
            currentNote.render(target)
        }

        console.log("HELLO %o", note);
    };


    this.ws.onmessage = function(msg) {
        var payload = undefined

        if(msg.data){
            payload = $.parseJSON(msg.data);
        }
        console.log("Message received %o %o", msg, payload);
        var op = payload.op;
        var data = payload.data;

        if(op == "NOTE"){        // note data
            notebook.setNote(data.note)
        }
    };
    
    this.ws.onopen = function(response) {
        console.log("Websocket created %o", response);
    };

    this.ws.onerror = function(response){
        console.log("On error %o", response);
    };

    this.ws.onclose = function(response){
        console.log("On close %o", response);
    };

    this.send = function(o){
        this.ws.send(JSON.stringify(o))
    };


    // Create new notebook
    this.newNote = function(){
        this.send({ op : "NEW_NOTE" });
    };


    // Open note
    this.openNote = function(noteId){
        
    };
};


function Note(data){
    this.data = data;     // Note.java

    this.render = function(target){
    }

    this.destroy = function(){
    }
};


function Pargraph(id){
    this.id = id;
    
    render = function(target){
    }
};


var nb = new Notebook({
    socket : "ws://localhost:8081",
    target : $('#notebook')
});
setTimeout(function(){
    nb.newNote();
}, 500);
console.log(">>>>>>>>> READY <<<<<<<<<<<");
