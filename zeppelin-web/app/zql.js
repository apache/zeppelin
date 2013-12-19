function zql(zep) {

    zeppelin = zep;

    this.create = function(listener, scope) {
        zeppelin.get("/zql/new", listener, scope);
    }

    this.set = function(sessionId, name, zql, params, cron, listener, scope){
        zeppelin.post("/zql/set/"+sessionId, {
            zql : zql,
            name : name,
            params : params,
            cron : cron
        }, listener, scope);
    }

    this.setZql = function(sessionId, zql, listener, scope){
        zeppelin.post("/zql/set/"+sessionId+"/zql", {
            zql : zql
        }, listener, scope);
    }

    this.setName = function(sessionId, name, listener, scope){
        zeppelin.post("/zql/set/"+sessionId+"/name", {
            name : name
        }, listener, scope);
    }

    this.setParams = function(sessionId, params, listener, scope){
        zeppelin.post("/zql/set/"+sessionId+"/params", {
            params : params
        }, listener, scope);
    }

    this.setCron = function(sessionId, cron, listener, scope){
        zeppelin.post("/zql/set/"+sessionId+"/cron", {
            cron : cron
        }, listener, scope);
    }

    this.run = function(id, listener, scope){
        zeppelin.get("/zql/run/"+id, listener, scope);
    }

    this.dryRun = function(id, listener, scope){
        zeppelin.get("/zql/run/"+id+"/dry", listener, scope);
    }

    this.abort = function(sessionId, listener, scope){
        zeppelin.get('/zql/abort/'+sessionId, listener, scope);
    }

    this.get = function(sessionId, listener, scope){
        zeppelin.get('/zql/get/'+sessionId, listener, scope);
    }

    this.del = function(sessionId, listener, scope){
        zeppelin.get('/zql/del/'+sessionId, listener, scope);
    }

    this.list = function(listener, scope){
        zeppelin.get("/zql/list", listener, scope);
    }

    this.listHistory = function(sessionId, listener, scope){
        zeppelin.get("/zql/history/list/"+sessionId, listener, scope);
    }

    this.getHistory = function(sessionId, historyId, listener, scope){
        zeppelin.get("/zql/history/get/"+sessionId+"/"+historyId, listener, scope);       
    }

    this.delHistory = function(sessionId, historyId, listener, scope){
        zeppelin.get("/zql/history/del/"+sessionId+"/"+historyId, listener, scope);       
    }
}

module.exports.zql = zql
