namespace java com.nflabs.zeppelin.interpreter.thrift


struct RemoteInterpreterContext {
  1: string paragraphId,
  2: string paragraphTitle,
  3: string paragraphText,
  4: string config,   // json serialized config
  5: string gui       // json serialized gui
}

struct RemoteInterpreterResult {
  1: string code,
  2: string type,
  3: string msg,
  4: string config,   // json serialized config
  5: string gui       // json serialized gui
}

service RemoteInterpreterService {
  void createInterpreter(1: string className, 2: map<string, string> properties);

  void open(1: string className);
  void close(1: string className);
  RemoteInterpreterResult interpret(1: string className, 2: string st, 3: RemoteInterpreterContext interpreterContext);
  void cancel(1: string className, 2: RemoteInterpreterContext interpreterContext);
  i32 getProgress(1: string className, 2: RemoteInterpreterContext interpreterContext);
  string getFormType(1: string className);
  list<string> completion(1: string className, 2: string buf, 3: i32 cursor);
  void shutdown();

  string getStatus(1:string jobId);
}