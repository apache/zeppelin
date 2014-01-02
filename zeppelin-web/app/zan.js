function zan(zep) {

    zeppelin = zep;

    this.search = function(queryString, listener, scope) {
        zeppelin.post("/zan/search", queryString, listener, scope);
    }

    this.running = function(listener, scope){
	zeppelin.get("/zan/running", listener, scope);
    }

    this.update = function(listener, scope){
	zeppelin.get("/zan/update", listener, scope);
    }

    this.install = function(libName, listener, scope){
	zeppelin.get("/zan/install/"+libName, listener, scope);
    }

    this.upgrade = function(libName, listener, scope){
	zeppelin.get("/zan/upgrade/"+libName, listener, scope);
    }

    this.uninstall = function(libName, listener, scope){
	zeppelin.get("/zan/uninstall/"+libName, listener, scope);
    }

}

module.exports.zan = zan
