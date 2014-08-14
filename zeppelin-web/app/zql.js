function zql(zep) {

    zeppelin = zep;

    this.create = function(listener, scope) {
        zeppelin.post("/zql/job", "", listener, scope);
    }

    this.set = function(jobId, name, zql, params, cron, listener, scope){
        zeppelin.post("/zql/job/"+jobId, {
            zql : zql,
            name : name,
            params : params,
            cron : cron
        }, listener, scope);
    }

    this.setZql = function(jobId, zql, listener, scope){
        zeppelin.post("/zql/job/"+jobId+"/zql", {
            zql : zql
        }, listener, scope);
    }

    this.setName = function(jobId, name, listener, scope){
        zeppelin.post("/zql/job/"+jobId+"/name", {
            name : name
        }, listener, scope);
    }

    this.setParams = function(jobId, params, listener, scope){
        zeppelin.post("/zql/job/"+jobId+"/params", {
            params : params
        }, listener, scope);
    }

    this.setCron = function(jobId, cron, listener, scope){
        zeppelin.post("/zql/job/"+jobId+"/cron", {
            cron : cron
        }, listener, scope);
    }

    this.run = function(id, listener, scope){
        zeppelin.get("/zql/run/"+id, listener, scope);
    }

    this.dryRun = function(id, listener, scope){
        zeppelin.get("/zql/run/"+id+"/dry", listener, scope);
    }

    this.abort = function(jobId, listener, scope){
        zeppelin.get('/zql/abort/'+jobId, listener, scope);
    }

    this.get = function(jobId, listener, scope){
        zeppelin.get('/zql/'+jobId, listener, scope);
    }

    this.del = function(jobId, listener, scope){
        zeppelin.del('/zql/'+jobId, listener, scope);
    }

    this.list = function(listener, scope){
        zeppelin.get("/zql/", listener, scope);
    }

    this.getTree = function(listener, scope){
        zeppelin.get("/zql/job/tree", listener, scope);
    }

    this.setTree = function(jobTree, listener, scope){
        zeppelin.post("/zql/job/tree", jobTree, listener, scope);
    }

    this.listHistory = function(jobId, listener, scope){
        zeppelin.get("/zql/history/"+jobId, listener, scope);
    }

    this.getHistory = function(jobId, historyId, listener, scope){
        zeppelin.get("/zql/history/"+jobId+"/"+historyId, listener, scope);
    }

    this.delHistory = function(jobId, historyId, listener, scope){
        zeppelin.del("/zql/history/"+jobId+"/"+historyId, listener, scope);
    }
}

module.exports.zql = zql
