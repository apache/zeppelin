function Notebook(config){
    var notebook = this;
    this.config = config;

    this.ws = new WebSocket(config.socket);

    // current note
    this.currentNote;

    this.setNote = function(data){
        console.log("setNote %o", data);
        var target = $(config.target.selector);

        if(this.currentNote) {
            if(this.currentNote.data.id == data.id){
                // update current note
                this.currentNote.refresh(data)
                console.log("update curret note");
            } else {
                // change to another note
                this.currentNote.destroy();
                this.currentNote = new Note(this, data)
                this.currentNote.render(target)
            }
        } else {
            // load note
            this.currentNote = new Note(this, data)
            this.currentNote.render(target)
        }
    };


    this.ws.onmessage = function(msg) {
        var payload = undefined

        if(msg.data){
            payload = $.parseJSON(msg.data);
        }
        console.log("RECEIVE << %o, %o", payload.op, payload);
        var op = payload.op;
        var data = payload.data;

        if(op == "NOTE"){        // note data
            notebook.setNote(data.note)
        } else if(op == "NOTES_INFO") { // list of notes
            // nothing to do. raise event
        }
        
        if(notebook.listener){
            notebook.listener.onMessage(op, data);
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
        console.log("SEND >> %o, %o", o.op, o);
        this.ws.send(JSON.stringify(o))
    };

    this.setListener = function(listener){
        this.listener = listener;
    };

    // Create new notebook
    this.newNote = function(){
        this.send({ op : "NEW_NOTE" });
    };


    // Open note
    this.openNote = function(noteId){
        this.send({ op : "GET_NOTE", data : { id : noteId}});
    };

    // list all notebooks
    this.listNotes = function(){
        this.send({op : "LIST_NOTES" });
    };    
};


function Note(notebook, data){
    this.notebook = notebook;
    this.data = data;               // backdata

    this.paragraphs = [];
    this.target;

    // refresh data 
    this.refresh = function(data){
        console.log("Note.refresh %o", data);
        this.destroy();
        this.data = data;
        this.render(this.target);
    };

    this.render = function(target){
        this.target = target;

        console.log("Note.render %o", this.data);
        var paragraphs = this.data.paragraphs;
        if(!paragraphs || paragraphs.length==0){
            console.log("No paragraph found!");
        } else {
            for(var i=0; i< paragraphs.length; i++){
                var p = new Paragraph(this.notebook, paragraphs[i]);
                this.paragraphs.push(p);

                target.append("<div id='"+p.data.id+"'></div>");
                p.render($('#'+p.data.id));                
            }
        }
    }

    this.destroy = function(){
        this.target.empty();
        this.data = undefined;
        this.paragraphs = [];
    }
};

function Paragraph(notebook, data){
    this.notebook = notebook;
    this.data = data;
    this.target;
    
    // refresh paragraph with new data
    this.refresh = function(data){
        
    };

    this.render = function(target){
        var p = this;
        this.target = target;
        target.html('<textarea></textarea>'+
                    '<div class="zeppelin paragraph result"></div>');
        target.children('textarea').val(this.data.paragraph);
        target.on('keypress', 'textarea', function(e){
            if(e.shiftKey && e.keyCode==13){  // shift + enter
                p.run();
                return false;                 // discard the event
            }
        });

        var result = this.data.result;
        if (result) {
            target.children('div').html(this.data.result.msg);
        }
    }


    this.run = function(){
        console.log("Run paragraph");
        var paragraph = this.target.children('textarea').val();

        notebook.send({
            op : "RUN_PARAGRAPH",
            data : {
                id : this.data.id,
                paragraph : paragraph
            }
        });
    };

    this.destroy = function(){
    };
};


var nb = new Notebook({
    socket : "ws://localhost:8081",
    target : $('#notebook')
});

nb.setListener({
    onMessage : function(op, data){
        console.log("onMessage %o, %o", op, data);
        if(op=="NOTES_INFO"){
            if(data.notes && data.notes.length>0){
                // display list of notebooks
                var html = "";
                for(var i=0; i<data.notes.length; i++){
                    var noteInfo = data.notes[i];
                    html += "<button id="+noteInfo.id+">"+noteInfo.id+"</button>\n"
                }
                $('#notebookList').html(html);

                for(var i=0; i<data.notes.length; i++){
                    var noteInfo = data.notes[i];
                    $('#'+noteInfo.id).on('click', function(noteId){ 
                        var id = noteId;
                        return function(event){ 
                            console.log("event %o, noteId=%o", event, id);
                            nb.openNote(id);
                        }
                    }(noteInfo.id));
                }
            }
            
        }
    }
});


setTimeout(function(){
    nb.listNotes();
    $('#newNotebookButton').on('click', function(){
        nb.newNote();
    });
}, 300);
console.log(">>>>>>>>> READY <<<<<<<<<<<");
