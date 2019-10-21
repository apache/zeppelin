export type EditorMode =
  | 'ace/mode/scala'
  | 'ace/mode/python'
  | 'ace/mode/r'
  | 'ace/mode/sql'
  | 'ace/mode/markdown'
  | 'ace/mode/sh';

export type EditorCompletionKey = 'TAB' | string;
export type EditorLanguage = 'scala' | 'python' | 'r' | 'sql' | 'markdown' | 'sh' | string;

export interface Ticket {
  principal: string;
  ticket: string;
  redirectURL?: string;
  roles: string;
}

export interface ConfigurationsInfo {
  configurations: {
    'zeppelin.war.tempdir': string;
    'zeppelin.notebook.azure.user': string;
    'zeppelin.helium.npm.installer.url': string;
    'zeppelin.notebook.git.remote.username': string;
    'zeppelin.interpreter.remoterunner': string;
    'zeppelin.notebook.s3.user': string;
    'zeppelin.server.port': string;
    'zeppelin.plugins.dir': string;
    'zeppelin.notebook.new_format.delete_old': string;
    'zeppelin.ssl.truststore.type': string;
    'zeppelin.ssl.keystore.path': string;
    'zeppelin.notebook.s3.bucket': string;
    'zeppelin.notebook.git.remote.access-token': string;
    'zeppelin.recovery.dir': string;
    'zeppelin.notebook.s3.timeout': string;
    'zeppelin.notebook.cron.enable': string;
    'zeppelin.server.addr': string;
    'zeppelin.username.force.lowercase': string;
    'zeppelin.ssl.keystore.type': string;
    'zeppelin.ssl.truststore.path': string;
    'zeppelin.notebook.dir': string;
    'zeppelin.interpreter.lifecyclemanager.class': string;
    'zeppelin.notebook.gcs.dir': string;
    'zeppelin.notebook.s3.sse': string;
    'zeppelin.websocket.max.text.message.size': string;
    'zeppelin.notebook.git.remote.origin': string;
    'zeppelin.server.authorization.header.clear': string;
    isRevisionSupported: string;
    'zeppelin.interpreter.dep.mvnRepo': string;
    'zeppelin.ssl': string;
    'zeppelin.notebook.autoInterpreterBinding': string;
    'zeppelin.config.storage.class': string;
    'zeppelin.helium.node.installer.url': string;
    'zeppelin.cluster.heartbeat.interval': string;
    'zeppelin.notebook.storage': string;
    'zeppelin.notebook.new_format.convert': string;
    'zeppelin.interpreter.dir': string;
    'zeppelin.anonymous.allowed': string;
    'zeppelin.credentials.persist': string;
    'zeppelin.notebook.mongo.uri': string;
    'zeppelin.config.fs.dir': string;
    'zeppelin.server.allowed.origins': string;
    'zeppelin.notebook.mongo.database': string;
    'zeppelin.encoding': string;
    'zeppelin.server.jetty.request.header.size': string;
    'zeppelin.search.temp.path': string;
    'zeppelin.cluster.heartbeat.timeout': string;
    'zeppelin.notebook.s3.endpoint': string;
    'zeppelin.notebook.homescreen.hide': string;
    'zeppelin.scheduler.threadpool.size': string;
    'zeppelin.notebook.azure.share': string;
    'zeppelin.helium.yarnpkg.installer.url': string;
    'zeppelin.server.strict.transport': string;
    'zeppelin.interpreter.setting': string;
    'zeppelin.server.xxss.protection': string;
    'zeppelin.server.rpc.portRange': string;
    'zeppelin.war': string;
    'zeppelin.interpreter.output.limit': string;
    'zeppelin.dep.localrepo': string;
    'zeppelin.interpreter.max.poolsize': string;
    'zeppelin.server.ssl.port': string;
    'zeppelin.notebook.mongo.collection': string;
    'zeppelin.notebook.public': string;
    'zeppelin.helium.registry': string;
    'zeppelin.server.kerberos.principal': string;
    'zeppelin.server.default.dir.allowed': string;
    'zeppelin.ssl.client.auth': string;
    'zeppelin.server.context.path': string;
    'zeppelin.recovery.storage.class': string;
    'zeppelin.notebook.default.owner.username': string;
    'zeppelin.home': string;
    'zeppelin.interpreter.lifecyclemanager.timeout.threshold': string;
    'zeppelin.cluster.addr': string;
    'zeppelin.notebook.git.remote.url': string;
    'zeppelin.notebook.mongo.autoimport': string;
    'zeppelin.notebook.one.way.sync': string;
    'zeppelin.notebook.homescreen': string;
    'zeppelin.interpreter.connect.timeout': string;
    'zeppelin.server.xframe.options': string;
    'zeppelin.interpreter.lifecyclemanager.timeout.checkinterval': string;
    'zeppelin.server.kerberos.keytab': string;
    'zeppelin.interpreter.rpc.portRange': string;
    'zeppelin.interpreter.group.default': string;
    'zeppelin.conf.dir': string;
    'zeppelin.interpreter.localRepo': string;
    'zeppelin.notebook.collaborative.mode.enable': string;
    'zeppelin.search.use.disk': string;
  };
}

export interface ErrorInfo {
  info?: string;
}

export interface AuthInfo {
  info?: string;
}
