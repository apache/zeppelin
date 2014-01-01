function zan(zep) {

    zeppelin = zep;

    this.search = function(queryString, listener, scope) {
        zeppelin.post("/zan/search", queryString, listener, scope);
    }
}

module.exports.zan = zan
